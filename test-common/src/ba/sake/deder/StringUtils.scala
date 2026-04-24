package ba.sake.deder

object StringUtils {

  def recommend(attempt: String, allEntries: Seq[String]): Seq[String] = {
    // entries that startWith(attempt) take priority on Levenshtein distance
    val startWithTaskNames = allEntries.filter(_.startsWith(attempt)).sortBy(_.length)
    val nearestTaskNames = locally {
      val nearest = allEntries
        .map(entry => entry -> levenshtein(attempt, entry))
        .sortBy(_._2)
      if nearest.isEmpty then Seq.empty
      else {
        val firstDistance = nearest.head._2
        nearest
          .takeWhile(_._2 == firstDistance)
          .map(_._1)
      }
    }
    if startWithTaskNames.nonEmpty then startWithTaskNames.take(10)
    else nearestTaskNames.take(5)
  }

  private def levenshtein(s1: String, s2: String): Int = {
    val dists = (0 to s2.length).toList
    s1.foldLeft(dists) { (prevRow, s1Elem) =>
      prevRow.zip(prevRow.tail).zip(s2).scanLeft(prevRow.head + 1) { case (left, ((diag, up), s2Elem)) =>
        List(left + 1, up + 1, diag + (if (s1Elem == s2Elem) 0 else 1)).min
      }
    }.last
  }
}
