import { Link } from 'react-router-dom'
import type { BracketMatchup, BracketTeam, SeasonBracket } from '../api/leagues'

interface PlayoffBracketsProps {
  bracket: SeasonBracket
}

type BracketKind = 'winners' | 'toilet'

/** Renders a season's playoff bracket(s). Both lists empty (Pick'em, or playoffs not started
 * yet — see SeasonBracket) means nothing to show at all. */
export default function PlayoffBrackets({ bracket }: PlayoffBracketsProps) {
  if (bracket.winnersBracket.length === 0 && bracket.toiletBowlBracket.length === 0) {
    return null
  }

  return (
    <div className="brackets">
      {bracket.winnersBracket.length > 0 && (
        <Bracket title="Playoffs" kind="winners" matchups={bracket.winnersBracket} />
      )}
      {bracket.toiletBowlBracket.length > 0 && (
        <Bracket title="Toilet Bowl" kind="toilet" matchups={bracket.toiletBowlBracket} />
      )}
    </div>
  )
}

function Bracket({ title, kind, matchups }: { title: string; kind: BracketKind; matchups: BracketMatchup[] }) {
  const rounds = groupByRound(matchups)
  return (
    <section className="bracket-section">
      <h3 className="card-title">{title}</h3>
      <div className="bracket-scroll">
        <div className="bracket-rounds">
          {rounds.map(([round, roundMatchups]) => (
            <div key={round} className="bracket-round">
              <div className="bracket-round-title">Round {round}</div>
              {roundMatchups.map((matchup) => (
                <MatchupCard key={matchup.matchupId} matchup={matchup} kind={kind} />
              ))}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function groupByRound(matchups: BracketMatchup[]): [number, BracketMatchup[]][] {
  const rounds = new Map<number, BracketMatchup[]>()
  for (const matchup of matchups) {
    const list = rounds.get(matchup.round) ?? []
    list.push(matchup)
    rounds.set(matchup.round, list)
  }
  return Array.from(rounds.entries()).sort(([a], [b]) => a - b)
}

function placementLabel(placement: number | null, kind: BracketKind): string | null {
  if (placement === null) {
    return null
  }
  if (kind === 'toilet') {
    // Only the toilet bowl's own final is a meaningful title — its other placement games use a
    // locally-restarted numbering that doesn't correspond to a real overall standing.
    return placement === 1 ? 'Toilet Bowl' : null
  }
  if (placement === 1) return 'Championship'
  if (placement === 3) return '3rd Place'
  return `${placement}th Place`
}

function MatchupCard({ matchup, kind }: { matchup: BracketMatchup; kind: BracketKind }) {
  const label = placementLabel(matchup.placement, kind)
  const decided = Boolean(matchup.team1?.winner || matchup.team2?.winner)
  return (
    <div className="bracket-matchup">
      {label && <div className="bracket-matchup-label">{label}</div>}
      <TeamRow team={matchup.team1} decided={decided} />
      <TeamRow team={matchup.team2} decided={decided} />
    </div>
  )
}

function TeamRow({ team, decided }: { team: BracketTeam | null; decided: boolean }) {
  if (!team) {
    return <div className="bracket-team bracket-team-tbd">TBD</div>
  }

  const content = (
    <>
      {team.avatarUrl && <img src={team.avatarUrl} alt="" className="avatar" />}
      {team.teamName}
    </>
  )

  const className = `bracket-team${team.winner ? ' bracket-team-winner' : decided ? ' bracket-team-loser' : ''}`

  return (
    <div className={className}>
      {team.ownerUserId ? <Link to={`/managers/${team.ownerUserId}`}>{content}</Link> : content}
    </div>
  )
}
