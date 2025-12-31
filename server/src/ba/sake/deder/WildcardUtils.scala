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
}
