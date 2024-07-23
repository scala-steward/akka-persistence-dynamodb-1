/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.dynamodb

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

import akka.actor.typed.ActorSystem
import akka.annotation.InternalStableApi
import akka.util.Helpers
import com.typesafe.config.Config
import software.amazon.awssdk.core.retry.RetryMode

object DynamoDBSettings {

  /**
   * Scala API: Load configuration from `akka.persistence.dynamodb`.
   */
  def apply(system: ActorSystem[_]): DynamoDBSettings =
    apply(system.settings.config.getConfig("akka.persistence.dynamodb"))

  /**
   * Java API: Load configuration from `akka.persistence.dynamodb`.
   */
  def create(system: ActorSystem[_]): DynamoDBSettings =
    apply(system)

  /**
   * Scala API: From custom configuration corresponding to `akka.persistence.dynamodb`.
   */
  def apply(config: Config): DynamoDBSettings = {
    val journalTable: String = config.getString("journal.table")

    val journalPublishEvents: Boolean = config.getBoolean("journal.publish-events")

    val snapshotTable: String = config.getString("snapshot.table")

    val querySettings = new QuerySettings(config.getConfig("query"))

    new DynamoDBSettings(journalTable, journalPublishEvents, snapshotTable, querySettings)
  }

  /**
   * Java API: From custom configuration corresponding to `akka.persistence.dynamodb`.
   */
  def create(config: Config): DynamoDBSettings =
    apply(config)

}

final class DynamoDBSettings private (
    val journalTable: String,
    val journalPublishEvents: Boolean,
    val snapshotTable: String,
    val querySettings: QuerySettings) {

  val journalBySliceGsi: String = journalTable + "_slice_idx"
  val snapshotBySliceGsi: String = snapshotTable + "_slice_idx"
}

final class QuerySettings(config: Config) {
  val refreshInterval: FiniteDuration = config.getDuration("refresh-interval").toScala
  val behindCurrentTime: FiniteDuration = config.getDuration("behind-current-time").toScala
  val backtrackingEnabled: Boolean = config.getBoolean("backtracking.enabled")
  val backtrackingWindow: FiniteDuration = config.getDuration("backtracking.window").toScala
  val backtrackingBehindCurrentTime: FiniteDuration = config.getDuration("backtracking.behind-current-time").toScala
  val bufferSize: Int = config.getInt("buffer-size")
  val deduplicateCapacity: Int = config.getInt("deduplicate-capacity")
  val startFromSnapshotEnabled: Boolean = config.getBoolean("start-from-snapshot.enabled")
}

object ClientSettings {
  final class HttpSettings(
      val maxConcurrency: Int,
      val maxPendingConnectionAcquires: Int,
      val readTimeout: FiniteDuration,
      val writeTimeout: FiniteDuration,
      val connectionTimeout: FiniteDuration,
      val connectionAcquisitionTimeout: FiniteDuration,
      val connectionTimeToLive: FiniteDuration,
      val useIdleConnectionReaper: Boolean,
      val connectionMaxIdleTime: FiniteDuration,
      val tlsNegotiationTimeout: FiniteDuration,
      val tcpKeepAlive: Boolean) {

    override def toString: String =
      s"HttpSettings(" +
      s"maxConcurrency=$maxConcurrency, " +
      s"maxPendingConnectionAcquires=$maxPendingConnectionAcquires, " +
      s"readTimeout=${readTimeout.toCoarsest}, " +
      s"writeTimeout=${writeTimeout.toCoarsest}, " +
      s"connectionTimeout=${connectionTimeout.toCoarsest}, " +
      s"connectionAcquisitionTimeout=${connectionAcquisitionTimeout.toCoarsest}, " +
      s"connectionTimeToLive=${connectionTimeToLive.toCoarsest}, " +
      s"useIdleConnectionReaper=$useIdleConnectionReaper, " +
      s"connectionMaxIdleTime=${connectionMaxIdleTime.toCoarsest}, " +
      s"tlsNegotiationTimeout=${tlsNegotiationTimeout.toCoarsest}, " +
      s"tcpKeepAlive=$tcpKeepAlive)"
  }

  object HttpSettings {
    def apply(clientConfig: Config): HttpSettings = {
      val config = clientConfig.getConfig("http")
      new HttpSettings(
        maxConcurrency = config.getInt("max-concurrency"),
        maxPendingConnectionAcquires = config.getInt("max-pending-connection-acquires"),
        readTimeout = config.getDuration("read-timeout").toScala,
        writeTimeout = config.getDuration("write-timeout").toScala,
        connectionTimeout = config.getDuration("connection-timeout").toScala,
        connectionAcquisitionTimeout = config.getDuration("connection-acquisition-timeout").toScala,
        connectionTimeToLive = config.getDuration("connection-time-to-live").toScala,
        useIdleConnectionReaper = config.getBoolean("use-idle-connection-reaper"),
        connectionMaxIdleTime = config.getDuration("connection-max-idle-time").toScala,
        tlsNegotiationTimeout = config.getDuration("tls-negotiation-timeout").toScala,
        tcpKeepAlive = config.getBoolean("tcp-keep-alive"))
    }
  }

  final class RetrySettings(val mode: RetryMode, val numRetries: Option[Int]) {
    override def toString: String =
      s"RetryPolicySettings(" +
      s"mode=$mode, " +
      s"numRetries=${numRetries.fold("default")(_.toString)})"
  }

  object RetrySettings {
    def get(clientConfig: Config): Option[RetrySettings] = {
      val config = clientConfig.getConfig("retry-policy")
      if (config.getBoolean("enabled")) {
        val mode = Helpers.toRootLowerCase(config.getString("retry-mode")) match {
          case "default"  => RetryMode.defaultRetryMode()
          case "legacy"   => RetryMode.LEGACY
          case "standard" => RetryMode.STANDARD
          case "adaptive" => RetryMode.ADAPTIVE
        }
        Some(new RetrySettings(mode = mode, numRetries = optInt(config, "num-retries")))
      } else None
    }
  }

  final class CompressionSettings(val enabled: Boolean, val thresholdBytes: Int) {
    override def toString: String =
      s"CompressionSettings(" +
      s"enabled=$enabled, " +
      s"thresholdBytes=$thresholdBytes)"
  }

  object CompressionSettings {
    def apply(clientConfig: Config): CompressionSettings = {
      val config = clientConfig.getConfig("compression")
      new CompressionSettings(
        enabled = config.getBoolean("enabled"),
        thresholdBytes = config.getBytes("threshold").toInt)
    }
  }

  final class LocalSettings(val host: String, val port: Int) {
    override def toString = s"LocalSettings(host=$host, port=$port)"
  }

  object LocalSettings {
    def get(clientConfig: Config): Option[LocalSettings] = {
      val config = clientConfig.getConfig("local")
      if (config.getBoolean("enabled")) {
        Some(new LocalSettings(config.getString("host"), config.getInt("port")))
      } else None
    }
  }

  def apply(config: Config): ClientSettings =
    new ClientSettings(
      callTimeout = config.getDuration("call-timeout").toScala,
      callAttemptTimeout = optDuration(config, "call-attempt-timeout"),
      http = HttpSettings(config),
      retry = RetrySettings.get(config),
      compression = CompressionSettings(config),
      region = optString(config, "region"),
      local = LocalSettings.get(config))

  private def optString(config: Config, path: String): Option[String] = {
    if (config.hasPath(path)) {
      val value = config.getString(path)
      if (value.nonEmpty) Some(value) else None
    } else None
  }

  private def optDuration(config: Config, path: String): Option[FiniteDuration] = {
    Helpers.toRootLowerCase(config.getString(path)) match {
      case "off" | "none" => None
      case _              => Some(config.getDuration(path).toScala)
    }
  }

  private def optInt(config: Config, path: String): Option[Int] = {
    Helpers.toRootLowerCase(config.getString(path)) match {
      case "default" => None
      case _         => Some(config.getInt(path))
    }
  }
}

final class ClientSettings(
    val callTimeout: FiniteDuration,
    val callAttemptTimeout: Option[FiniteDuration],
    val http: ClientSettings.HttpSettings,
    val retry: Option[ClientSettings.RetrySettings],
    val compression: ClientSettings.CompressionSettings,
    val region: Option[String],
    val local: Option[ClientSettings.LocalSettings]) {

  override def toString: String =
    s"ClientSettings(" +
    s"callTimeout=${callTimeout.toCoarsest}, " +
    s"callAttemptTimeout=${callAttemptTimeout.map(_.toCoarsest)}, " +
    s"http=$http, " +
    s"retry=$retry, " +
    s"compression=$compression, " +
    s"region=$region, " +
    s"local=$local)"
}

/**
 * INTERNAL API
 */
@InternalStableApi
final class PublishEventsDynamicSettings(config: Config) {
  val throughputThreshold: Int = config.getInt("throughput-threshold")
  val throughputCollectInterval: FiniteDuration = config.getDuration("throughput-collect-interval").toScala
}
