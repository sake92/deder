package ba.sake.deder

import ba.sake.tupson.JsonRW

case class CompileResult(
    classesDir: DederPath,
    errors: Int,
    warnings: Int,
    sourceCount: Int
) derives JsonRW {
  def success: Boolean = errors == 0
}
