package ba.sake.deder

object WildcardUtils {

  private val globChar = '%' // like in SQL

  def getMatches(allEntries: Seq[String], matcher: String): Seq[String] = {
    val matcherParts = matcher.split(globChar).toSeq
    val regex = matcher.replaceAll("%", ".*")
    val finalRegex = s"^${regex}$$"
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
