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

object CompileResult {
  // Custom Hashable that includes actual class file content hash, not just the path string.
  // Without this, JsonRW-derived Hashable (low-priority fallback) only hashes the JSON
  // representation — which serializes classesDir as a path string, missing any file content
  // changes. Downstream CachedTasks (jar, publishArtifacts) would then hit cache with stale results.
  given Hashable[CompileResult] with {
    def hashStr(value: CompileResult): String =
      val classesHash = Hashable[DederPath].hashStr(value.classesDir)
      val combined = s"${classesHash}-${value.errors}-${value.warnings}-${value.sourceCount}"
      combined.hashStr
  }
}
