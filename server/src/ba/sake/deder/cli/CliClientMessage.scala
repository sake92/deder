package ba.sake.deder.cli

import ba.sake.tupson.JsonRW

import java.util.UUID

enum CliClientMessage derives JsonRW {
  case Help(args: Seq[String])
  case Version()
  case Modules(args: Seq[String])
  case Tasks(args: Seq[String])
  case Plan(args: Seq[String])
  case Exec(requestId: String, args: Seq[String])
  case Cancel(requestId: String)
  case Clean(args: Seq[String])
  case Import(args: Seq[String])
  case Shutdown()

  def getRequestId: String = this match {
    case CliClientMessage.Exec(requestId, _) => requestId
    case CliClientMessage.Cancel(requestId)  => requestId
    case _                            => UUID.randomUUID.toString
  }
}
