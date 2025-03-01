/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.dynamodb

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object TestConfig {
  lazy val config: Config = {
    val defaultConfig = ConfigFactory.load()

    ConfigFactory
      .parseString("""
      akka.loglevel = DEBUG
      akka.persistence.journal.plugin = "akka.persistence.dynamodb.journal"
      akka.persistence.snapshot-store.plugin = "akka.persistence.dynamodb.snapshot"
      akka.persistence.dynamodb.client.local.enabled = true
      akka.actor.testkit.typed.default-timeout = 10s
      """)
      .withFallback(defaultConfig)
  }

  val backtrackingDisabledConfig: Config =
    ConfigFactory.parseString("akka.persistence.dynamodb.query.backtracking.enabled = off")

}
