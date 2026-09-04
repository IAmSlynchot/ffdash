import { Link } from 'react-router-dom'
import type { BracketMatchup, BracketTeam, SeasonBracket } from '../api/leagues'

interface PlayoffBracketsProps {
  bracket: SeasonBracket
}

// Layout constants for the classic bracket drawing below — every card and connector line is
// positioned from these, in one shared coordinate space, so lines always meet cards exactly.
const CARD_WIDTH = 190
const CARD_HEIGHT = 66
const ROW_HEIGHT = 84 // vertical space each matchup "slot" occupies, including its own gap
const COLUMN_GAP = 46

/** Renders a season's playoff bracket(s). Both lists empty (Pick'em, or playoffs not started
 * yet — see SeasonBracket) means nothing to show at all. */
export default function PlayoffBrackets({ bracket }: PlayoffBracketsProps) {
  if (bracket.winnersBracket.length === 0 && bracket.toiletBowlBracket.length === 0) {
    return null
  }

  return (
    <div className="brackets">
      {bracket.winnersBracket.length > 0 && <Bracket title="Playoffs" matchups={bracket.winnersBracket} />}
      {bracket.toiletBowlBracket.length > 0 && <Bracket title="Toilet Bowl" matchups={bracket.toiletBowlBracket} />}
    </div>
  )
}

function Bracket({ title, matchups }: { title: string; matchups: BracketMatchup[] }) {
  // A matchup that just advances winners/losers into a later round (no placement) belongs to
  // the main bracket "chain", drawn as a classic converging tree. A matchup that settles a
  // minor placement instead (3rd place, 5th place, ...) is a standalone side game, shown
  // separately below rather than forced into the tree. A bracket's own final (placement === 1)
  // IS the chain's last node, not a side game.
  const chainRounds = groupByRound(matchups.filter((m) => m.placement === null || m.placement === 1))
  const sideGames = matchups
    .filter((m) => m.placement !== null && m.placement !== 1)
    .sort((a, b) => (a.placementRank ?? 0) - (b.placementRank ?? 0))

  return (
    <section className="bracket-section">
      <h3 className="card-title">{title}</h3>
      {chainRounds.length > 0 && <BracketChain rounds={chainRounds} />}
      {sideGames.length > 0 && (
        <div className="bracket-side-games">
          {sideGames.map((matchup) => (
            <div key={matchup.matchupId} className="bracket-card bracket-card-static">
              <MatchupBody matchup={matchup} />
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function groupByRound(matchups: BracketMatchup[]): BracketMatchup[][] {
  const byRound = new Map<number, BracketMatchup[]>()
  for (const matchup of matchups) {
    const list = byRound.get(matchup.round) ?? []
    list.push(matchup)
    byRound.set(matchup.round, list)
  }
  return Array.from(byRound.entries())
    .sort(([a], [b]) => a - b)
    .map(([, ms]) => ms.slice().sort((a, b) => a.matchupId - b.matchupId))
}

interface CardPosition {
  matchup: BracketMatchup
  x: number
  y: number // vertical center
}

interface Connector {
  x1: number
  y1: number
  x2: number
  y2: number
}

/**
 * Lays out a bracket's chain rounds purely from each round's match count — no need to trace
 * Sleeper's actual t1_from/t2_from source references. A round's N matches are spaced evenly
 * (index i centered at (i + 0.5)/N of the shared height), and a matchup at index i in one round
 * connects to index floor(i * nextN / N) in the next. That formula happens to cover both real
 * shapes: adjacent rounds of equal size (a bye — one team advances untouched, so it's a straight
 * across line) and a round that's exactly half the previous (a real pairing, so two lines
 * converge on one) — and for evenly spaced positions, a converging pair's midpoint lands exactly
 * on the next round's own center, so the two cases connect seamlessly with one shared formula.
 */
function BracketChain({ rounds }: { rounds: BracketMatchup[][] }) {
  const maxCount = Math.max(...rounds.map((r) => r.length))
  const totalHeight = maxCount * ROW_HEIGHT
  const totalWidth = rounds.length * CARD_WIDTH + (rounds.length - 1) * COLUMN_GAP

  const positions: CardPosition[][] = rounds.map((matches, r) =>
    matches.map((matchup, i) => ({
      matchup,
      x: r * (CARD_WIDTH + COLUMN_GAP),
      y: (totalHeight * (i + 0.5)) / matches.length,
    })),
  )

  const connectors: Connector[] = []
  for (let r = 0; r < positions.length - 1; r++) {
    const sourceCount = positions[r].length
    const targetCount = positions[r + 1].length
    for (let i = 0; i < sourceCount; i++) {
      const targetIndex = Math.floor((i * targetCount) / sourceCount)
      connectors.push({
        x1: positions[r][i].x + CARD_WIDTH,
        y1: positions[r][i].y,
        x2: positions[r + 1][targetIndex].x,
        y2: positions[r + 1][targetIndex].y,
      })
    }
  }

  return (
    <div className="bracket-scroll">
      <div style={{ width: totalWidth }}>
        <div className="bracket-round-headers">
          {rounds.map((_, r) => (
            <div key={r} className="bracket-round-title" style={{ width: CARD_WIDTH }}>
              Round {r + 1}
            </div>
          ))}
        </div>
        <div className="bracket-canvas" style={{ width: totalWidth, height: totalHeight }}>
          <svg className="bracket-lines" width={totalWidth} height={totalHeight}>
            {connectors.map((c, i) => (
              <path
                key={i}
                className="bracket-connector"
                d={`M ${c.x1} ${c.y1} H ${(c.x1 + c.x2) / 2} V ${c.y2} H ${c.x2}`}
              />
            ))}
          </svg>
          {positions.flat().map(({ matchup, x, y }) => (
            <div
              key={matchup.matchupId}
              className="bracket-card"
              style={{ left: x, top: y - CARD_HEIGHT / 2, width: CARD_WIDTH, height: CARD_HEIGHT }}
            >
              <MatchupBody matchup={matchup} />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function MatchupBody({ matchup }: { matchup: BracketMatchup }) {
  const decided = Boolean(matchup.team1?.winner || matchup.team2?.winner)
  return (
    <>
      <div className="bracket-matchup-label">{matchup.placementLabel}</div>
      <TeamRow team={matchup.team1} decided={decided} />
      <TeamRow team={matchup.team2} decided={decided} />
    </>
  )
}

function TeamRow({ team, decided }: { team: BracketTeam | null; decided: boolean }) {
  if (!team) {
    return <div className="bracket-team bracket-team-tbd">TBD</div>
  }

  const content = (
    <>
      {team.avatarUrl && <img src={team.avatarUrl} alt="" className="avatar" />}
      <span className="bracket-team-name">{team.teamName}</span>
    </>
  )

  const className = `bracket-team${team.winner ? ' bracket-team-winner' : decided ? ' bracket-team-loser' : ''}`

  return (
    <div className={className}>
      {team.ownerUserId ? <Link to={`/managers/${team.ownerUserId}`}>{content}</Link> : content}
    </div>
  )
}
