package com.blogspot.nurkiewicz.akka.demo.fs

import java.io.File
import java.nio.file.Path

/**
 * @author Tomasz Nurkiewicz
 * @since 3/30/13, 10:13 PM
 */
sealed trait FileSystemChange

case class Created(fileOrDir: File) extends FileSystemChange

case class Deleted(fileOrDir: File) extends FileSystemChange

case class MonitorDir(path: Path)
