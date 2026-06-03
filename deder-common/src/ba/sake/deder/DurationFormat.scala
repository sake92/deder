package ba.sake.deder

import java.time.Duration

extension (d: Duration) {
  def toPrettyString: String = {
    val hours = d.toHours
    val minutes = d.toMinutes % 60
    val seconds = d.toSeconds % 60 + (d.toNanos % 1_000_000_000) / 1_000_000_000.0
    if (hours > 0) f"${hours}h${minutes}m${seconds}%.2fs"
    else if (minutes > 0) f"${minutes}m${seconds}%.2fs"
    else f"${seconds}%.2fs"
  }
}
