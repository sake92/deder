package ba.sake.deder

object WildcardUtils {

  def getMatches(allEntries: Seq[String], matcher: String): Seq[String] = {
    val regex = matcher.replaceAll("%", ".*")
    allEntries.filter(_.matches(regex)).distinct
  }

  def getMatches(allEntries: Seq[String], matchers: Seq[String]): Seq[String] =
    matchers.flatMap(getMatches(allEntries, _)).distinct

  def getMatchesOrRecommendations(allEntries: Seq[String], matchers: Seq[String]): Either[Seq[String], Seq[String]] = {
    val res = getMatches(allEntries, matchers)
    if res.isEmpty then
      Left {
        val normalizedMatchers = matchers.map(_.replaceAll("%", ""))
        normalizedMatchers.flatMap(m => StringUtils.recommend(m, allEntries))
      }
    else Right(res)
  }
}
