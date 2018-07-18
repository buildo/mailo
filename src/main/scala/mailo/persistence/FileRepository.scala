package mailo.persistence

import scala.util.{Failure, Success, Try}

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import com.typesafe.scalalogging.LazyLogging

case class Offset(value: Long) extends AnyVal
case class PersistedStatus(offset: Offset)
object PersistedStatus {
  def default: PersistedStatus = PersistedStatus(Offset(0))
}

class FileRepository(path: String) extends LazyLogging {
  import collection.JavaConverters._

  private[this] val filePath = Paths.get(path)
  private[this] val charset = Charset.forName("UTF-8")

  if (!Files.exists(filePath)) {
    Files.createFile(filePath)
    writeStatusToFile(PersistedStatus.default)
  }

  def get: Either[String, PersistedStatus] =
    Try {
      val serializedContent: String =
        Files.readAllLines(filePath, charset).asScala.head
      decode[PersistedStatus](serializedContent)
    } match {
      case Success(status) =>
        status.left
          .map(circeError => circeError.getLocalizedMessage)
          .flatMap(result => Right(result))
      case Failure(error) => Left(error.getLocalizedMessage)
    }

  def unsafeGet: PersistedStatus = get match {
    case Left(errorMsg) =>
      logger.error(s"unable to get status: $errorMsg")
      PersistedStatus.default
    case Right(status) => status
  }

  private[this] def writeStatusToFile(status: PersistedStatus) =
    Files.write(filePath, List(status.asJson.noSpaces).asJava, charset)

  def put(status: PersistedStatus): Either[String, PersistedStatus] =
    Try(writeStatusToFile(status)) match {
      case Success(_)     => Right(status)
      case Failure(error) => Left(error.getLocalizedMessage)
    }

  def set(update: PersistedStatus => PersistedStatus)
    : Either[String, PersistedStatus] = synchronized {
    get.flatMap { status =>
      val updatedStatus = update(status)
      Try(writeStatusToFile(updatedStatus)) match {
        case Success(_)     => Right(updatedStatus)
        case Failure(error) => Left(error.getLocalizedMessage)
      }
    }
  }
}
