package ba.sake.deder.deps

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.{JsonRW, toJson}
import coursierapi.FetchResult
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import ba.sake.deder.GraphUtils

case class DepCoord(
    organization: String,
    name: String,
    version: String
) derives JsonRW {
  def key: String = s"${organization}:${name}"
  def repr: String = s"${organization}:${name}:${version}"
}

case class DepNode(
    coord: DepCoord,
    artifactPath: Option[String],
    sizeBytes: Long,
    isDirect: Boolean,
    requestedVersions: Seq[String],
    selectedVersions: Seq[String],
    evictedRequestedVersions: Seq[String],
    isConflict: Boolean
) derives JsonRW

case class DepEdge(
    from: String,
    to: String,
    kind: String
) derives JsonRW

case class DepConflict(
    key: String,
    requestedVersions: Seq[String],
    selectedVersions: Seq[String],
    evictedRequestedVersions: Seq[String],
    requestedPathExamples: Seq[String]
) derives JsonRW

case class DependencyGraphData(
    moduleId: String,
    nodes: Seq[DepNode],
    edges: Seq[DepEdge],
    directCount: Int,
    transitiveCount: Int,
    totalSizeBytes: Long,
    conflicts: Seq[DepConflict]
) derives JsonRW

case class DependencyReportOptions(
    maxDepth: Int = Int.MaxValue,
    directOnly: Boolean = false,
    includePatterns: Seq[String] = Seq.empty,
    excludePatterns: Seq[String] = Seq.empty,
    whySelector: Option[String] = None
) {
  def includeTransitive: Boolean = !directOnly && maxDepth > 1
  def includeAnyDeps: Boolean = maxDepth > 0
}

object DependencyReportOptions {

  def fromTaskArgs(args: Seq[String]): Either[String, DependencyReportOptions] = {
    var idx = 0
    var maxDepth = Int.MaxValue
    var directOnly = false
    val include = scala.collection.mutable.ArrayBuffer.empty[String]
    val exclude = scala.collection.mutable.ArrayBuffer.empty[String]
    var why: Option[String] = None

    def nextRequired(flag: String): Either[String, String] =
      if idx + 1 >= args.length then Left(s"Missing value for ${flag}")
      else {
        idx += 1
        Right(args(idx))
      }

    while idx < args.length do {
      args(idx) match {
        case "--max-depth" =>
          nextRequired("--max-depth") match {
            case Left(err) => return Left(err)
            case Right(value) =>
              try {
                maxDepth = value.toInt
                if maxDepth < 0 then return Left("--max-depth must be non-negative")
              } catch {
                case _: NumberFormatException => return Left(s"Invalid --max-depth value '${value}'")
              }
          }
        case "--direct-only" | "--no-transitive" =>
          directOnly = true
        case "--include" =>
          nextRequired("--include") match {
            case Left(err)    => return Left(err)
            case Right(value) => include += value
          }
        case "--exclude" =>
          nextRequired("--exclude") match {
            case Left(err)    => return Left(err)
            case Right(value) => exclude += value
          }
        case "--why" =>
          nextRequired("--why") match {
            case Left(err)    => return Left(err)
            case Right(value) => why = Some(value)
          }
        case other =>
          return Left(s"Unknown dependency report option: ${other}")
      }
      idx += 1
    }

    Right(
      DependencyReportOptions(
        maxDepth = maxDepth,
        directOnly = directOnly,
        includePatterns = include.toSeq,
        excludePatterns = exclude.toSeq,
        whySelector = why
      )
    )
  }
}

object DependencyGraphBuilder {

  def build(moduleId: String, directDependencies: Seq[Dependency], fetchResult: FetchResult): DependencyGraphData = {
    val requested = directDependencies.map(_.applied)
    val requestedByKey = requested
      .groupMap(r => (r.getModule.getOrganization, r.getModule.getName))(_.getVersion)
      .view
      .mapValues(_.distinct.sorted)
      .toMap

    val resolvedDeps = fetchResult.getDependencies.asScala.toSeq
    val artifacts = fetchResult.getArtifacts.asScala.toSeq

    val resolvedWithArtifacts: Seq[(coursierapi.Dependency, Option[java.io.File])] =
      resolvedDeps.zipWithIndex.map { case (dep, idx) =>
        val fileOpt =
          if idx < artifacts.size then Option(artifacts(idx).getValue)
          else None
        (dep, fileOpt)
      }

    val selectedByKey = resolvedDeps
      .groupMap(d => (d.getModule.getOrganization, d.getModule.getName))(_.getVersion)
      .view
      .mapValues(_.distinct.sorted)
      .toMap

    val conflictByKey: Map[(String, String), DepConflict] =
      (requestedByKey.keySet ++ selectedByKey.keySet).flatMap { key =>
        val reqVers = requestedByKey.getOrElse(key, Seq.empty)
        val selVers = selectedByKey.getOrElse(key, Seq.empty)
        val evicted = reqVers.diff(selVers)
        val isConflict = reqVers.size > 1 || selVers.size > 1 || evicted.nonEmpty
        if !isConflict then None
        else
          Some(
            key -> DepConflict(
              key = s"${key._1}:${key._2}",
              requestedVersions = reqVers,
              selectedVersions = selVers,
              evictedRequestedVersions = evicted,
              requestedPathExamples = reqVers.map(v => s"${moduleId} -> ${key._1}:${key._2}:${v}")
            )
          )
      }.toMap

    val directKeys = requestedByKey.keySet

    val nodes = resolvedWithArtifacts
      .map { case (dep, fileOpt) =>
        val org = dep.getModule.getOrganization
        val name = dep.getModule.getName
        val ver = dep.getVersion
        val key = (org, name)
        val size = fileOpt.map(_.length()).getOrElse(0L)
        val reqVers = requestedByKey.getOrElse(key, Seq.empty)
        val selVers = selectedByKey.getOrElse(key, Seq.empty)
        val evicted = reqVers.diff(selVers)
        DepNode(
          coord = DepCoord(org, name, ver),
          artifactPath = fileOpt.map(_.getAbsolutePath),
          sizeBytes = size,
          isDirect = directKeys.contains(key),
          requestedVersions = reqVers,
          selectedVersions = selVers,
          evictedRequestedVersions = evicted,
          isConflict = conflictByKey.contains(key)
        )
      }
      .groupBy(_.coord.repr)
      .values
      .map(_.head)
      .toSeq
      .sortBy(_.coord.repr)

    val edges = nodes.map { n =>
      DepEdge(
        from = s"module:${moduleId}",
        to = s"dep:${n.coord.repr}",
        kind = if n.isDirect then "direct" else "transitive"
      )
    }

    DependencyGraphData(
      moduleId = moduleId,
      nodes = nodes,
      edges = edges,
      directCount = nodes.count(_.isDirect),
      transitiveCount = nodes.count(!_.isDirect),
      totalSizeBytes = nodes.map(_.sizeBytes).sum,
      conflicts = conflictByKey.values.toSeq.sortBy(_.key)
    )
  }
}

object DependencyReportRenderers {

  def applyFilters(data: DependencyGraphData, options: DependencyReportOptions): DependencyGraphData = {
    if !options.includeAnyDeps then
      data.copy(nodes = Seq.empty, edges = Seq.empty, directCount = 0, transitiveCount = 0, totalSizeBytes = 0L, conflicts = Seq.empty)
    else {
      val depthFilteredNodes =
        if options.includeTransitive then data.nodes
        else data.nodes.filter(_.isDirect)

      val includeFiltered =
        if options.includePatterns.isEmpty then depthFilteredNodes
        else depthFilteredNodes.filter(n => options.includePatterns.exists(p => wildcardMatches(n.coord.repr, p) || wildcardMatches(n.coord.key, p)))

      val excludeFiltered =
        includeFiltered.filterNot(n => options.excludePatterns.exists(p => wildcardMatches(n.coord.repr, p) || wildcardMatches(n.coord.key, p)))

      val allowedTo = excludeFiltered.map(n => s"dep:${n.coord.repr}").toSet
      val filteredEdges = data.edges.filter(e => allowedTo.contains(e.to))
      val conflictKeys = excludeFiltered.filter(_.isConflict).map(_.coord.key).toSet
      val filteredConflicts = data.conflicts.filter(c => conflictKeys.contains(c.key))

      data.copy(
        nodes = excludeFiltered.sortBy(_.coord.repr),
        edges = filteredEdges,
        directCount = excludeFiltered.count(_.isDirect),
        transitiveCount = excludeFiltered.count(!_.isDirect),
        totalSizeBytes = excludeFiltered.map(_.sizeBytes).sum,
        conflicts = filteredConflicts
      )
    }
  }

  def renderTree(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val sb = new StringBuilder
    sb.append(renderConflictSummary(data))
    sb.append(s"${data.moduleId}\n")
    if data.nodes.isEmpty then sb.append("  (no dependencies after filters)\n")
    else {
      val direct = data.nodes.filter(_.isDirect)
      val trans = data.nodes.filter(!_.isDirect)
      sb.append("  direct:\n")
      if direct.isEmpty then sb.append("    - (none)\n")
      else direct.foreach(n => sb.append(s"    - ${nodeLabel(n)}${conflictSuffix(n)}\n"))
      if options.includeTransitive then {
        sb.append("  transitive:\n")
        if trans.isEmpty then sb.append("    - (none)\n")
        else trans.foreach(n => sb.append(s"    - ${nodeLabel(n)}${conflictSuffix(n)}\n"))
      }
    }
    sb.toString
  }

  def renderList(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val sb = new StringBuilder
    sb.append(renderConflictSummary(data))
    if data.nodes.isEmpty then sb.append("(no dependencies after filters)\n")
    else
      data.nodes.sortBy(_.coord.repr).foreach { n =>
        val kind = if n.isDirect then "D" else "T"
        sb.append(s"[${kind}] ${nodeLabel(n)}${conflictSuffix(n)}\n")
      }
    sb.toString
  }

  def renderWhy(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val selectorOpt = options.whySelector.map(_.trim).filter(_.nonEmpty)
    if selectorOpt.isEmpty then return "Missing selector. Use --why <org:name|org:name:version|pattern%>."
    val selector = selectorOpt.get
    val matching = data.nodes.filter { n =>
      wildcardMatches(n.coord.repr, selector) || wildcardMatches(n.coord.key, selector)
    }
    val sb = new StringBuilder
    sb.append(renderConflictSummary(data))
    if matching.isEmpty then sb.append(s"No dependencies matched '${selector}'.\n")
    else {
      matching.sortBy(_.coord.repr).foreach { n =>
        sb.append(s"${data.moduleId} -> ${nodeLabel(n)}${conflictSuffix(n)}\n")
        if n.evictedRequestedVersions.nonEmpty then
          sb.append(s"  requested (not selected): ${n.evictedRequestedVersions.mkString(", ")}\n")
      }
    }
    sb.toString
  }

  def renderStats(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val sb = new StringBuilder
    sb.append(renderConflictSummary(data))
    sb.append(s"module: ${data.moduleId}\n")
    sb.append(s"total dependencies: ${data.nodes.size}\n")
    sb.append(s"direct: ${data.directCount}\n")
    sb.append(s"transitive: ${data.transitiveCount}\n")
    sb.append(s"total size: ${humanBytes(data.totalSizeBytes)}\n")
    if data.nodes.nonEmpty then {
      sb.append("largest artifacts:\n")
      data.nodes.sortBy(n => -n.sizeBytes).take(10).foreach { n =>
        sb.append(s"  - ${nodeLabel(n)} (${humanBytes(n.sizeBytes)})${conflictSuffix(n)}\n")
      }
    }
    sb.toString
  }

  def renderDot(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    val moduleVertex = s"module:${data.moduleId}"
    g.addVertex(moduleVertex)
    data.nodes.foreach { n =>
      val depVertex = s"dep:${n.coord.repr}"
      g.addVertex(depVertex)
      if !g.containsEdge(moduleVertex, depVertex) then g.addEdge(moduleVertex, depVertex)
    }

    GraphUtils.generateDOT(
      g,
      v => sanitizeId(v),
      v => {
        if v.startsWith("module:") then
          Map(
            "label" -> data.moduleId,
            "shape" -> "box",
            "style" -> "filled",
            "fillcolor" -> "#dfefff"
          )
        else {
          val repr = v.stripPrefix("dep:")
          val node = data.nodes.find(_.coord.repr == repr).get
          val base =
            Map(
              "label" -> s"${node.coord.key}\\n${node.coord.version}",
              "shape" -> "ellipse"
            ) ++
              (if node.isDirect then Map("penwidth" -> "2") else Map.empty)
          if node.isConflict then
            val color = if node.evictedRequestedVersions.nonEmpty then "#f9a825" else "#d32f2f"
            base ++ Map("style" -> "filled", "fillcolor" -> color)
          else base
        }
      }
    )
  }

  def renderMermaid(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val moduleVertex = s"module:${data.moduleId}"
    val sb = new StringBuilder
    sb.append("flowchart LR\n")
    sb.append(s"  ${sanitizeId(moduleVertex)}[\"${data.moduleId}\"]\n")
    data.nodes.foreach { n =>
      val depVertex = s"dep:${n.coord.repr}"
      sb.append(s"  ${sanitizeId(depVertex)}[\"${n.coord.key}\\n${n.coord.version}\"]\n")
    }
    data.nodes.foreach { n =>
      val depVertex = s"dep:${n.coord.repr}"
      sb.append(s"  ${sanitizeId(moduleVertex)} --> ${sanitizeId(depVertex)}\n")
    }
    sb.append("  classDef module fill:#dfefff,stroke:#1a73e8,color:#0b1f44\n")
    sb.append("  classDef direct stroke-width:2px\n")
    sb.append("  classDef conflict fill:#ffebee,stroke:#d32f2f,color:#4a1010\n")
    sb.append("  classDef evicted fill:#fff8e1,stroke:#f9a825,color:#3b2a00\n")
    sb.append(s"  class ${sanitizeId(moduleVertex)} module\n")
    val directIds = data.nodes.filter(_.isDirect).map(n => sanitizeId(s"dep:${n.coord.repr}"))
    if directIds.nonEmpty then sb.append(s"  class ${directIds.mkString(",")} direct\n")
    val conflictIds = data.nodes.filter(n => n.isConflict && n.evictedRequestedVersions.isEmpty).map(n => sanitizeId(s"dep:${n.coord.repr}"))
    if conflictIds.nonEmpty then sb.append(s"  class ${conflictIds.mkString(",")} conflict\n")
    val evictedIds = data.nodes.filter(_.evictedRequestedVersions.nonEmpty).map(n => sanitizeId(s"dep:${n.coord.repr}"))
    if evictedIds.nonEmpty then sb.append(s"  class ${evictedIds.mkString(",")} evicted\n")
    sb.toString
  }

  def renderHtml(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val json = data.toJson
    val dot = renderDot(data, options).replace("`", "\\`")
    val mermaid = renderMermaid(data, options).replace("`", "\\`")
    val template = """<!doctype html>
<html>
<head>
  <meta charset=\"utf-8\" />
  <title>Dependency report - __MODULE__</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 24px; }
    .stats { margin-bottom: 16px; }
    .controls { display:flex; gap:12px; align-items:center; margin: 12px 0; flex-wrap: wrap; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; }
    tr.conflict { background: #ffebee; }
    tr.evicted { background: #fff8e1; }
    code { white-space: pre-wrap; }
    button { padding: 6px 10px; }
  </style>
</head>
<body>
  <h1>Dependency report: __MODULE__</h1>
  <div class=\"stats\">
    <div>Total dependencies: <strong>__TOTAL_DEPS__</strong></div>
    <div>Direct: <strong>__DIRECT_COUNT__</strong>, Transitive: <strong>__TRANSITIVE_COUNT__</strong></div>
    <div>Total size: <strong>__TOTAL_SIZE__</strong></div>
    <div>Conflicts: <strong>__CONFLICTS__</strong></div>
  </div>
  <div class=\"controls\">
    <label>Search <input id=\"search\" /></label>
    <label><input id=\"conflictOnly\" type=\"checkbox\" /> conflict only</label>
    <label><input id=\"showTransitive\" type=\"checkbox\" checked /> show transitive</label>
    <button id=\"downloadJson\">Download JSON</button>
    <button id=\"downloadDot\">Download DOT</button>
    <button id=\"downloadMermaid\">Download Mermaid</button>
  </div>
  <table>
    <thead>
      <tr>
        <th>kind</th><th>dependency</th><th>version</th><th>size</th><th>requested</th><th>selected</th><th>evicted requested</th>
      </tr>
    </thead>
    <tbody id=\"rows\"></tbody>
  </table>
  <script>
    const data = __JSON__;
    const dot = `__DOT__`;
    const mermaid = `__MERMAID__`;
    const rowsEl = document.getElementById('rows');
    const searchEl = document.getElementById('search');
    const conflictEl = document.getElementById('conflictOnly');
    const transitiveEl = document.getElementById('showTransitive');

    const bytes = (n) => {
      if (n < 1024) return `${n} B`;
      const units = ['KB','MB','GB','TB'];
      let v = n / 1024;
      let i = 0;
      while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
      return `${v.toFixed(2)} ${units[i]}`;
    };

    function render() {
      const q = searchEl.value.trim().toLowerCase();
      const conflictOnly = conflictEl.checked;
      const showTransitive = transitiveEl.checked;
      const filtered = data.nodes.filter(n => {
        const full = `${n.coord.organization}:${n.coord.name}:${n.coord.version}`.toLowerCase();
        if (q && !full.includes(q)) return false;
        if (conflictOnly && !n.isConflict) return false;
        if (!showTransitive && !n.isDirect) return false;
        return true;
      });
      rowsEl.innerHTML = filtered.map(n => {
        const cls = n.evictedRequestedVersions.length ? 'evicted' : (n.isConflict ? 'conflict' : '');
        return `<tr class="${cls}">
          <td>${n.isDirect ? 'direct' : 'transitive'}</td>
          <td>${n.coord.organization}:${n.coord.name}</td>
          <td>${n.coord.version}</td>
          <td>${bytes(n.sizeBytes)}</td>
          <td>${n.requestedVersions.join(', ')}</td>
          <td>${n.selectedVersions.join(', ')}</td>
          <td>${n.evictedRequestedVersions.join(', ')}</td>
        </tr>`;
      }).join('');
    }

    function download(name, content, type) {
      const blob = new Blob([content], { type });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = name;
      a.click();
      URL.revokeObjectURL(url);
    }

    document.getElementById('downloadJson').addEventListener('click', () =>
      download(`dependencies-${data.moduleId}.json`, JSON.stringify(data, null, 2), 'application/json'));
    document.getElementById('downloadDot').addEventListener('click', () =>
      download(`dependencies-${data.moduleId}.dot`, dot, 'text/plain'));
    document.getElementById('downloadMermaid').addEventListener('click', () =>
      download(`dependencies-${data.moduleId}.mmd`, mermaid, 'text/plain'));

    searchEl.addEventListener('input', render);
    conflictEl.addEventListener('change', render);
    transitiveEl.addEventListener('change', render);
    render();
  </script>
</body>
</html>
"""
    template
      .replace("__MODULE__", data.moduleId)
      .replace("__TOTAL_DEPS__", data.nodes.size.toString)
      .replace("__DIRECT_COUNT__", data.directCount.toString)
      .replace("__TRANSITIVE_COUNT__", data.transitiveCount.toString)
      .replace("__TOTAL_SIZE__", humanBytes(data.totalSizeBytes))
      .replace("__CONFLICTS__", data.conflicts.size.toString)
      .replace("__JSON__", json)
      .replace("__DOT__", dot)
      .replace("__MERMAID__", mermaid)
  }

  private def renderConflictSummary(data: DependencyGraphData): String = {
    if data.conflicts.isEmpty then ""
    else {
      val lines = data.conflicts.map { c =>
        val requested = if c.requestedVersions.nonEmpty then c.requestedVersions.mkString("/") else "-"
        val selected = if c.selectedVersions.nonEmpty then c.selectedVersions.mkString("/") else "-"
        val evicted = if c.evictedRequestedVersions.nonEmpty then s" evicted-requested=${c.evictedRequestedVersions.mkString(",")}" else ""
        s"  - ${c.key}: requested=${requested}, selected=${selected}${evicted}"
      }
      s"Conflict summary (${data.conflicts.size}):\n${lines.mkString("\n")}\n"
    }
  }

  private def nodeLabel(n: DepNode): String =
    s"${n.coord.repr} (${humanBytes(n.sizeBytes)})"

  private def conflictSuffix(n: DepNode): String =
    if !n.isConflict then ""
    else if n.evictedRequestedVersions.nonEmpty then s" [evicted requested: ${n.evictedRequestedVersions.mkString(", ")}]"
    else " [conflict]"

  private def wildcardMatches(value: String, pattern: String): Boolean = {
    val regex = "^" + java.util.regex.Pattern.quote(pattern).replace("%", "\\E.*\\Q") + "$"
    value.matches(regex)
  }

  private def humanBytes(bytes: Long): String = {
    if bytes < 1024 then s"${bytes} B"
    else {
      val units = Seq("KB", "MB", "GB", "TB")
      var value = bytes.toDouble / 1024.0
      var idx = 0
      while value >= 1024 && idx < units.length - 1 do {
        value = value / 1024.0
        idx += 1
      }
      f"${value}%.2f ${units(idx)}"
    }
  }

  private def sanitizeId(id: String): String =
    id.replaceAll("[^a-zA-Z0-9_]", "_")
}
