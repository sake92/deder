package ba.sake.deder.deps

import ba.sake.tupson.{JsonRW, toJson}
import dependency.api.ops.*
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

enum DependencySort {
  case Size
  case Name
}

object DependencySort {
  def fromString(value: String): Either[String, DependencySort] =
    value.trim.toLowerCase match {
      case "size" => Right(DependencySort.Size)
      case "name" => Right(DependencySort.Name)
      case other  => Left(s"Invalid --sort value '${other}', expected one of: size, name")
    }
}

enum DependencyPathMode {
  case Shortest
  case All
}

object DependencyPathMode {
  def fromString(value: String): Either[String, DependencyPathMode] =
    value.trim.toLowerCase match {
      case "shortest" => Right(DependencyPathMode.Shortest)
      case "all"      => Right(DependencyPathMode.All)
      case other      => Left(s"Invalid --path value '${other}', expected one of: shortest, all")
    }
}

case class DependencyReportOptions(
    maxDepth: Int = Int.MaxValue,
    includePatterns: Seq[String] = Seq.empty,
    excludePatterns: Seq[String] = Seq.empty,
    whySelector: Option[String] = None,
    whyPathMode: DependencyPathMode = DependencyPathMode.Shortest,
    sort: DependencySort = DependencySort.Size
) {
  def includeTransitive: Boolean = maxDepth > 1
  def includeAnyDeps: Boolean = maxDepth > 0
}

object DependencyReportOptions {

  def fromTaskArgs(args: Seq[String]): Either[String, DependencyReportOptions] = {
    var idx = 0
    var maxDepth = Int.MaxValue
    val include = scala.collection.mutable.ArrayBuffer.empty[String]
    val exclude = scala.collection.mutable.ArrayBuffer.empty[String]
    var why: Option[String] = None
    var whyPathMode = DependencyPathMode.Shortest
    var sort = DependencySort.Size

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
          maxDepth = math.min(maxDepth, 1)
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
        case "--path" =>
          nextRequired("--path") match {
            case Left(err) => return Left(err)
            case Right(value) =>
              DependencyPathMode.fromString(value) match {
                case Left(err)    => return Left(err)
                case Right(parsed) => whyPathMode = parsed
              }
          }
        case "--sort" =>
          nextRequired("--sort") match {
            case Left(err) => return Left(err)
            case Right(value) =>
              DependencySort.fromString(value) match {
                case Left(err)    => return Left(err)
                case Right(parsed) => sort = parsed
              }
          }
        case other =>
          return Left(s"Unknown dependency report option: ${other}")
      }
      idx += 1
    }

    Right(
      DependencyReportOptions(
        maxDepth = maxDepth,
        includePatterns = include.toSeq,
        excludePatterns = exclude.toSeq,
        whySelector = why,
        whyPathMode = whyPathMode,
        sort = sort
      )
    )
  }
}

object DependencyGraphBuilder {

  def build(moduleId: String, directDependencies: Seq[Dependency], resolvedGraph: ResolvedDependencyGraph): DependencyGraphData = {
    val requested = directDependencies.map(_.applied.toCs)
    val requestedByKey = requested
      .groupMap(r => (r.getModule.getOrganization.toString, r.getModule.getName.toString))(_.getVersion)
      .view
      .mapValues(_.map(_.toString).distinct.sorted)
      .toMap

    val selectedByKey = resolvedGraph.dependencies
      .groupMap(dep => (dep.organization, dep.name))(_.version)
      .view
      .mapValues(_.distinct.sorted)
      .toMap

    val directReprs = resolvedGraph.rootDependencies
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

    val nodes = resolvedGraph.dependencies
      .map { dep =>
        val key = (dep.organization, dep.name)
        val size = resolvedGraph.artifactFilesByDependency.get(dep.repr).map(_.length()).getOrElse(0L)
        val reqVers = requestedByKey.getOrElse(key, Seq.empty)
        val selVers = selectedByKey.getOrElse(key, Seq.empty)
        val evicted = reqVers.diff(selVers)
        DepNode(
          coord = DepCoord(dep.organization, dep.name, dep.version),
          artifactPath = resolvedGraph.artifactFilesByDependency.get(dep.repr).map(_.getAbsolutePath),
          sizeBytes = size,
          isDirect = directReprs.contains(dep.repr),
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

    val edges =
      (
        directReprs.toSeq.map { repr =>
          DepEdge(
            from = moduleVertex(moduleId),
            to = depVertex(repr),
            kind = "direct"
          )
        } ++
          resolvedGraph.parentDependencies.toSeq.flatMap { case (child, parents) =>
            parents.map { parent =>
              DepEdge(
                from = depVertex(parent),
                to = depVertex(child),
                kind = "dependency"
              )
            }
          }
      ).distinct
        .sortBy(edge => (edge.from, edge.to))

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

  private def moduleVertex(moduleId: String): String = s"module:${moduleId}"
  private def depVertex(repr: String): String = s"dep:${repr}"
}

object DependencyReportRenderers {

  def applyFilters(data: DependencyGraphData, options: DependencyReportOptions): DependencyGraphData = {
    if !options.includeAnyDeps then
      data.copy(nodes = Seq.empty, edges = Seq.empty, directCount = 0, transitiveCount = 0, totalSizeBytes = 0L, conflicts = Seq.empty)
    else {
      val depths = computeDepths(data)
      val depthFilteredNodes = data.nodes.filter { node =>
        depths.getOrElse(depVertex(node.coord.repr), Int.MaxValue) <= options.maxDepth
      }

      val includeFiltered =
        if options.includePatterns.isEmpty then depthFilteredNodes
        else depthFilteredNodes.filter(n => options.includePatterns.exists(p => wildcardMatches(n.coord.repr, p) || wildcardMatches(n.coord.key, p)))

      val excludeFiltered =
        includeFiltered.filterNot(n => options.excludePatterns.exists(p => wildcardMatches(n.coord.repr, p) || wildcardMatches(n.coord.key, p)))

      val allowedVertices = excludeFiltered.map(n => depVertex(n.coord.repr)).toSet
      val filteredEdges = data.edges.filter { edge =>
        if edge.from == moduleVertex(data) then allowedVertices.contains(edge.to)
        else allowedVertices.contains(edge.from) && allowedVertices.contains(edge.to)
      }
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
    sb.append(renderStatsHeader(data))
    sb.append(s"${data.moduleId}\n")
    if data.nodes.isEmpty then sb.append("  (no dependencies after filters)\n")
    else {
      val outgoing = outgoingEdges(data)
      val roots = sortVertices(
        outgoing.getOrElse(moduleVertex(data), Seq.empty).filter(nodeByVertex(data).contains),
        data,
        options
      )
      if roots.isEmpty then sb.append("  (no dependencies after filters)\n")
      else roots.foreach(appendTree(sb, _, "  ", Set.empty, data, options, outgoing))
    }
    sb.toString
  }

  def renderList(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val sb = new StringBuilder
    sb.append(renderConflictSummary(data))
    sb.append(renderStatsHeader(data))
    if data.nodes.isEmpty then sb.append("(no dependencies after filters)\n")
    else
      sortNodes(data.nodes, options).foreach { n =>
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
    sb.append(renderStatsHeader(data))
    if matching.isEmpty then sb.append(s"No dependencies matched '${selector}'.\n")
    else {
      matching.sortBy(_.coord.repr).foreach { node =>
        val targetVertex = depVertex(node.coord.repr)
        sb.append(s"${node.coord.repr}${conflictSuffix(node)}\n")
        options.whyPathMode match {
          case DependencyPathMode.Shortest =>
            shortestPath(data, targetVertex) match {
              case Some(path) => sb.append(s"  shortest path: ${renderPath(path, data)}\n")
              case None       => sb.append("  shortest path: (unreachable after filters)\n")
            }
          case DependencyPathMode.All =>
            val paths = allPaths(data, targetVertex)
            if paths.isEmpty then sb.append("  paths: (unreachable after filters)\n")
            else {
              sb.append(s"  paths (${paths.size}):\n")
              paths.foreach(path => sb.append(s"    - ${renderPath(path, data)}\n"))
            }
        }
        if node.evictedRequestedVersions.nonEmpty then
          sb.append(s"  requested (not selected): ${node.evictedRequestedVersions.mkString(", ")}\n")
      }
    }
    sb.toString
  }

  def renderStats(data0: DependencyGraphData, options: DependencyReportOptions): String =
    renderTree(data0, options)

  def renderDot(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val g = new SimpleDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    val module = moduleVertex(data)
    g.addVertex(module)
    data.nodes.foreach { n =>
      g.addVertex(depVertex(n.coord.repr))
    }
    data.edges.foreach { edge =>
      g.addVertex(edge.from)
      g.addVertex(edge.to)
      if !g.containsEdge(edge.from, edge.to) then g.addEdge(edge.from, edge.to)
    }

    GraphUtils.generateDOT(
      g,
      v => sanitizeId(v),
      v => {
        if v == module then
          Map(
            "label" -> data.moduleId,
            "shape" -> "box",
            "style" -> "filled",
            "fillcolor" -> "#dfefff"
          )
        else {
          val node = nodeByVertex(data)(v)
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
    val module = moduleVertex(data)
    val sb = new StringBuilder
    sb.append("flowchart LR\n")
    sb.append(s"""  ${sanitizeId(module)}["${data.moduleId}"]""" + "\n")
    sortNodes(data.nodes, options).foreach { n =>
      sb.append(s"""  ${sanitizeId(depVertex(n.coord.repr))}["${n.coord.key}\\n${n.coord.version}"]""" + "\n")
    }
    data.edges.sortBy(edge => (edge.from, edge.to)).foreach { edge =>
      sb.append(s"  ${sanitizeId(edge.from)} --> ${sanitizeId(edge.to)}\n")
    }
    sb.append("  classDef module fill:#dfefff,stroke:#1a73e8,color:#0b1f44\n")
    sb.append("  classDef direct stroke-width:2px\n")
    sb.append("  classDef conflict fill:#ffebee,stroke:#d32f2f,color:#4a1010\n")
    sb.append("  classDef evicted fill:#fff8e1,stroke:#f9a825,color:#3b2a00\n")
    sb.append(s"  class ${sanitizeId(module)} module\n")
    val directIds = data.nodes.filter(_.isDirect).map(n => sanitizeId(depVertex(n.coord.repr)))
    if directIds.nonEmpty then sb.append(s"  class ${directIds.mkString(",")} direct\n")
    val conflictIds = data.nodes.filter(n => n.isConflict && n.evictedRequestedVersions.isEmpty).map(n => sanitizeId(depVertex(n.coord.repr)))
    if conflictIds.nonEmpty then sb.append(s"  class ${conflictIds.mkString(",")} conflict\n")
    val evictedIds = data.nodes.filter(_.evictedRequestedVersions.nonEmpty).map(n => sanitizeId(depVertex(n.coord.repr)))
    if evictedIds.nonEmpty then sb.append(s"  class ${evictedIds.mkString(",")} evicted\n")
    sb.toString
  }

  def renderHtml(data0: DependencyGraphData, options: DependencyReportOptions): String = {
    val data = applyFilters(data0, options)
    val json = escapeJsonForScript(data.toJson)
    val dot = escapeTemplateLiteral(renderDot(data, options))
    val mermaid = escapeTemplateLiteral(renderMermaid(data, options))
    val template = """<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>Dependency report - __MODULE__</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 24px; color: #111827; }
    .stats { display:grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .card { border: 1px solid #d1d5db; border-radius: 10px; padding: 12px; background: #f9fafb; }
    .controls { display:flex; gap:12px; align-items:center; margin: 12px 0; flex-wrap: wrap; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; vertical-align: top; }
    thead { background: #f3f4f6; }
    tr.conflict { background: #ffebee; }
    tr.evicted { background: #fff8e1; }
    .muted { color: #6b7280; }
    .path { white-space: pre-wrap; font-family: ui-monospace, monospace; }
    button { padding: 6px 10px; }
    input[type="search"] { min-width: 280px; }
  </style>
</head>
<body>
  <h1>Dependency report: __MODULE__</h1>
  <div class="stats">
    <div class="card"><div class="muted">Total dependencies</div><strong>__TOTAL_DEPS__</strong></div>
    <div class="card"><div class="muted">Direct</div><strong>__DIRECT_COUNT__</strong></div>
    <div class="card"><div class="muted">Transitive</div><strong>__TRANSITIVE_COUNT__</strong></div>
    <div class="card"><div class="muted">Total size</div><strong>__TOTAL_SIZE__</strong></div>
    <div class="card"><div class="muted">Conflicts</div><strong>__CONFLICTS__</strong></div>
    <div class="card"><div class="muted">Visible rows</div><strong id="visibleCount">0</strong></div>
  </div>
  <div class="controls">
    <label>Search <input id="search" type="search" placeholder="org:name, version, or path" /></label>
    <label><input id="conflictOnly" type="checkbox" /> conflict only</label>
    <label><input id="directOnly" type="checkbox" /> direct only</label>
    <button id="downloadJson">Download JSON</button>
    <button id="downloadDot">Download DOT</button>
    <button id="downloadMermaid">Download Mermaid</button>
  </div>
  <table>
    <thead>
      <tr>
        <th>kind</th>
        <th>dependency</th>
        <th>version</th>
        <th>size</th>
        <th>shortest path</th>
        <th>requested</th>
        <th>selected</th>
        <th>evicted requested</th>
      </tr>
    </thead>
    <tbody id="rows"></tbody>
  </table>
  <script>
    const data = __JSON__;
    const dot = `__DOT__`;
    const mermaid = `__MERMAID__`;
    const rowsEl = document.getElementById('rows');
    const visibleCountEl = document.getElementById('visibleCount');
    const searchEl = document.getElementById('search');
    const conflictEl = document.getElementById('conflictOnly');
    const directEl = document.getElementById('directOnly');
    const moduleVertex = `module:${data.moduleId}`;

    const bytes = (n) => {
      if (n < 1024) return `${n} B`;
      const units = ['KB','MB','GB','TB'];
      let value = n / 1024;
      let idx = 0;
      while (value >= 1024 && idx < units.length - 1) { value /= 1024; idx++; }
      return `${value.toFixed(2)} ${units[idx]}`;
    };

    const escapeHtml = (value) =>
      String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    const nodeVertex = (node) => `dep:${node.coord.organization}:${node.coord.name}:${node.coord.version}`;
    const nodeMap = new Map(data.nodes.map((node) => [nodeVertex(node), node]));
    const outgoing = new Map();

    for (const edge of data.edges) {
      if (!outgoing.has(edge.from)) outgoing.set(edge.from, []);
      outgoing.get(edge.from).push(edge.to);
    }
    for (const values of outgoing.values()) {
      values.sort((left, right) => {
        const leftNode = nodeMap.get(left);
        const rightNode = nodeMap.get(right);
        const leftSize = leftNode ? leftNode.sizeBytes : 0;
        const rightSize = rightNode ? rightNode.sizeBytes : 0;
        if (rightSize !== leftSize) return rightSize - leftSize;
        return left.localeCompare(right);
      });
    }

    const shortestPaths = new Map([[moduleVertex, [moduleVertex]]]);
    const queue = [moduleVertex];
    while (queue.length > 0) {
      const current = queue.shift();
      const path = shortestPaths.get(current);
      for (const next of outgoing.get(current) || []) {
        if (!shortestPaths.has(next)) {
          shortestPaths.set(next, [...path, next]);
          queue.push(next);
        }
      }
    }

    const renderPath = (vertex) => {
      const path = shortestPaths.get(vertex);
      if (!path) return '(unreachable)';
      return path.map((step) => {
        if (step === moduleVertex) return data.moduleId;
        const node = nodeMap.get(step);
        return node ? `${node.coord.organization}:${node.coord.name}:${node.coord.version}` : step;
      }).join(' -> ');
    };

    function render() {
      const query = searchEl.value.trim().toLowerCase();
      const conflictOnly = conflictEl.checked;
      const directOnly = directEl.checked;

      const filtered = data.nodes
        .filter((node) => {
          if (conflictOnly && !node.isConflict) return false;
          if (directOnly && !node.isDirect) return false;
          const vertex = nodeVertex(node);
          const searchText = [
            `${node.coord.organization}:${node.coord.name}:${node.coord.version}`,
            renderPath(vertex),
            node.requestedVersions.join(', '),
            node.selectedVersions.join(', '),
            node.evictedRequestedVersions.join(', ')
          ].join(' ').toLowerCase();
          return !query || searchText.includes(query);
        })
        .sort((left, right) => {
          if (right.sizeBytes !== left.sizeBytes) return right.sizeBytes - left.sizeBytes;
          return `${left.coord.organization}:${left.coord.name}:${left.coord.version}`
            .localeCompare(`${right.coord.organization}:${right.coord.name}:${right.coord.version}`);
        });

      visibleCountEl.textContent = `${filtered.length} / ${data.nodes.length}`;
      rowsEl.innerHTML = filtered.map((node) => {
        const cls = node.evictedRequestedVersions.length ? 'evicted' : (node.isConflict ? 'conflict' : '');
        const vertex = nodeVertex(node);
        return `<tr class="${cls}">
          <td>${node.isDirect ? 'direct' : 'transitive'}</td>
          <td>${escapeHtml(`${node.coord.organization}:${node.coord.name}`)}</td>
          <td>${escapeHtml(node.coord.version)}</td>
          <td>${escapeHtml(bytes(node.sizeBytes))}</td>
          <td class="path">${escapeHtml(renderPath(vertex))}</td>
          <td>${escapeHtml(node.requestedVersions.join(', '))}</td>
          <td>${escapeHtml(node.selectedVersions.join(', '))}</td>
          <td>${escapeHtml(node.evictedRequestedVersions.join(', '))}</td>
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
    directEl.addEventListener('change', render);
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

  private def renderStatsHeader(data: DependencyGraphData): String =
    s"""module: ${data.moduleId}
total dependencies: ${data.nodes.size}
direct: ${data.directCount}
transitive: ${data.transitiveCount}
total size: ${humanBytes(data.totalSizeBytes)}
"""

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

  private def appendTree(
      sb: StringBuilder,
      vertex: String,
      indent: String,
      ancestors: Set[String],
      data: DependencyGraphData,
      options: DependencyReportOptions,
      outgoing: Map[String, Seq[String]]
  ): Unit = {
    val node = nodeByVertex(data)(vertex)
    sb.append(s"${indent}- ${nodeLabel(node)}${conflictSuffix(node)}\n")
    if ancestors.contains(vertex) then sb.append(s"${indent}  - (cycle detected)\n")
    else {
      val children = sortVertices(
        outgoing.getOrElse(vertex, Seq.empty).filter(nodeByVertex(data).contains),
        data,
        options
      )
      children.foreach(child => appendTree(sb, child, indent + "  ", ancestors + vertex, data, options, outgoing))
    }
  }

  private def shortestPath(data: DependencyGraphData, targetVertex: String): Option[Seq[String]] = {
    val outgoing = outgoingEdges(data)
    val queue = scala.collection.mutable.Queue(moduleVertex(data))
    val previous = scala.collection.mutable.Map(moduleVertex(data) -> Option.empty[String])
    while queue.nonEmpty do {
      val current = queue.dequeue()
      if current == targetVertex then
        val path = scala.collection.mutable.ArrayBuffer.empty[String]
        var cursor = Option(targetVertex)
        while cursor.nonEmpty do {
          path.prepend(cursor.get)
          cursor = previous(cursor.get)
        }
        return Some(path.toSeq)
      sortVertices(outgoing.getOrElse(current, Seq.empty).filter(v => v == targetVertex || nodeByVertex(data).contains(v)), data, DependencyReportOptions(sort = DependencySort.Name))
        .foreach { next =>
          if !previous.contains(next) then {
            previous(next) = Some(current)
            queue.enqueue(next)
          }
        }
    }
    None
  }

  private def allPaths(data: DependencyGraphData, targetVertex: String): Seq[Seq[String]] = {
    val outgoing = outgoingEdges(data)

    def visit(current: String, path: Vector[String], seen: Set[String]): Seq[Seq[String]] = {
      if current == targetVertex then Seq(path)
      else
        sortVertices(outgoing.getOrElse(current, Seq.empty).filter(v => v == targetVertex || nodeByVertex(data).contains(v)), data, DependencyReportOptions(sort = DependencySort.Name))
          .filterNot(seen)
          .flatMap(next => visit(next, path :+ next, seen + next))
    }

    visit(moduleVertex(data), Vector(moduleVertex(data)), Set(moduleVertex(data)))
      .sortBy(path => (path.size, renderPath(path, data)))
  }

  private def renderPath(path: Seq[String], data: DependencyGraphData): String =
    path.map {
      case vertex if vertex == moduleVertex(data) => data.moduleId
      case vertex                                 => nodeByVertex(data)(vertex).coord.repr
    }.mkString(" -> ")

  private def computeDepths(data: DependencyGraphData): Map[String, Int] = {
    val outgoing = outgoingEdges(data)
    val queue = scala.collection.mutable.Queue(moduleVertex(data))
    val depths = scala.collection.mutable.Map(moduleVertex(data) -> 0)
    while queue.nonEmpty do {
      val current = queue.dequeue()
      val nextDepth = depths(current) + 1
      outgoing.getOrElse(current, Seq.empty).foreach { next =>
        val existing = depths.get(next)
        if existing.forall(_ > nextDepth) then {
          depths(next) = nextDepth
          queue.enqueue(next)
        }
      }
    }
    depths.toMap
  }

  private def outgoingEdges(data: DependencyGraphData): Map[String, Seq[String]] =
    data.edges
      .groupMap(_.from)(_.to)
      .view
      .mapValues(_.distinct)
      .toMap

  private def sortVertices(vertices: Seq[String], data: DependencyGraphData, options: DependencyReportOptions): Seq[String] =
    sortNodes(vertices.flatMap(nodeByVertex(data).get), options).map(node => depVertex(node.coord.repr))

  private def sortNodes(nodes: Seq[DepNode], options: DependencyReportOptions): Seq[DepNode] =
    options.sort match {
      case DependencySort.Size =>
        nodes.sortBy(node => (-node.sizeBytes, node.coord.repr))
      case DependencySort.Name =>
        nodes.sortBy(_.coord.repr)
    }

  private def nodeByVertex(data: DependencyGraphData): Map[String, DepNode] =
    data.nodes.map(node => depVertex(node.coord.repr) -> node).toMap

  private def nodeLabel(n: DepNode): String =
    s"${n.coord.repr} (${humanBytes(n.sizeBytes)})"

  private def conflictSuffix(n: DepNode): String =
    if !n.isConflict then ""
    else if n.evictedRequestedVersions.nonEmpty then s" [evicted requested: ${n.evictedRequestedVersions.mkString(", ")}]"
    else " [conflict]"

  private def depVertex(repr: String): String = s"dep:${repr}"

  private def moduleVertex(data: DependencyGraphData): String = s"module:${data.moduleId}"

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

  private def escapeJsonForScript(value: String): String =
    value.replace("</script>", "<\\/script>")

  private def escapeTemplateLiteral(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("`", "\\`")
      .replace("${", "\\${")
      .replace("</script>", "<\\/script>")

  private def sanitizeId(id: String): String =
    id.replaceAll("[^a-zA-Z0-9_]", "_")
}
