package com.example.api.models
import java.time.*
import java.util.UUID
import org.typelevel.jawn.ast.JValue
import ba.sake.tupson.*
import ba.sake.validson.Validator
case class Pet(id: Long, name: String, tag: Option[String]) derives JsonRW