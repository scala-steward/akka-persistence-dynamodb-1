/**
 * Copyright (C) 2009-2024 Lightbend Inc. <http://www.lightbend.com>
 */
import sbt.{ Def, _ }
import sbt.Keys._

/**
 * Helper to set Automatic-Module-Name in projects.
 *
 * !! DO NOT BE TEMPTED INTO AUTOMATICALLY DERIVING THE NAMES FROM PROJECT NAMES !!
 *
 * The names carry a lot of implications and DO NOT have to always align 1:1 with the group ids or package names, though
 * there should be of course a strong relationship between them.
 */
object AutomaticModuleName {
  private val AutomaticModuleName = "Automatic-Module-Name"

  def settings(name: String): Seq[Def.Setting[Task[Seq[PackageOption]]]] = Seq(
    Compile / packageBin / packageOptions += Package.ManifestAttributes(AutomaticModuleName → name))
}
