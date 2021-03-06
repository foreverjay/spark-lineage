package org.chojin.spark.lineage

import java.util.Properties

import grizzled.slf4j.Logger

import scala.collection.JavaConversions._

object Config {
  private lazy val LOGGER = Logger[this.type]

  private final val prefix = "org.chojin.spark.lineage"
  private lazy val properties = {
    Option(getClass.getResourceAsStream("/lineage.properties"))
      .map({ stream =>
        val props = new Properties()
        props.load(stream)
        stream.close()

        props
      })
      .getOrElse(new Properties())
  }

  def get(name: String): String = properties.getProperty(name)

  def getList(name: String): Seq[String] = Option.apply(properties.getProperty(name))
    .flatMap(p => if (p.isEmpty) None else Some(p))
    .map(p => p.split("\\s*,\\s*").toSeq)
    .getOrElse(Seq())

  def createInstanceOf[T](suffix: String): T = {
    val propPrefix = s"$prefix.$suffix"
    val className = get(propPrefix)

    createInstance(className, propPrefix)
  }

  def createInstancesOf[T](suffix: String): List[T] = {
    val propPrefix = s"$prefix.$suffix"

    getList(s"${propPrefix}s").map(className => {
      createInstance[T](className, propPrefix)
    }).toList
  }

  private def createInstance[T](className: String, prefix: String): T = {
    try {
      def clazz = getClass.getClassLoader.loadClass(className)
      val configKey = clazz.getSimpleName.replaceFirst("Reporter$", "").toLowerCase

      val clazzPrefix = s"$prefix.$configKey"

      val props = properties
        .toMap
        .filter({ case (k, _) => k.startsWith(s"$clazzPrefix.")})
        .map({ case (k, v) => k.substring(clazzPrefix.length + 1) -> v})

      LOGGER.debug(s"Properties -> $props")

      clazz
        .getConstructor(classOf[Map[String, String]])
        .newInstance(props)
        .asInstanceOf[T]
    } catch {
      case e: Throwable => {
        LOGGER.error(s"Unable to create instance of $className", e)
        throw e
      }
    }
  }
}
