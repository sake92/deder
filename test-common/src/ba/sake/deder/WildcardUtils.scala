package ba.sake.deder

object WildcardUtils {

  def getMatches(allEntries: Seq[String], matcher: String): Seq[String] = {
    val regex = matcher.replaceAll("%", ".*")
    allEntries.filter(_.matches(regex)).distinct
  }

  def getMatches(allEntries: Seq[String], matchers: Seq[String]): Seq[String] = {
    val (includeMatchers, excludeMatchers) = matchers.partition(m => !m.startsWith("~"))

    // Stage 1: include set (union of all include matchers against all entries)
    val includeSet: Set[String] =
      if includeMatchers.isEmpty then allEntries.toSet
      else includeMatchers.flatMap(getMatches(allEntries, _)).toSet

    // Stage 2: subtract excluded
    if excludeMatchers.isEmpty then includeSet.toSeq
    else {
      val excludePatterns = excludeMatchers.map(_.drop(1))
      val excludeSet = excludePatterns.flatMap(getMatches(allEntries, _)).toSet
      includeSet.diff(excludeSet).toSeq
    }
  }

  def getMatchesOrRecommendations(allEntries: Seq[String], matchers: Seq[String]): Either[Seq[String], Seq[String]] = {
    val res = getMatches(allEntries, matchers)
    if res.isEmpty then
      Left {
        val normalizedMatchers = matchers.filterNot(_.startsWith("~")).map(_.replaceAll("%", ""))
        normalizedMatchers.flatMap(m => StringUtils.recommend(m, allEntries))
      }
    else Right(res)
  }
}
