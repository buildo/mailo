package mailo.persistence

import org.iq80.leveldb._
import org.fusesource.leveldbjni.JniDBFactory._
import java.io._
import java.nio.charset.StandardCharsets

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.util.{Failure, Success, Try}

class LevelDbRepository {
  private[this] val offsetKey = bytes("offset")

  private[this] val fact = org.fusesource.leveldbjni.JniDBFactory.factory
  private[this] val options = (new Options()).createIfMissing(true)
  private[this] val db = fact.open(new File("journal"), options)

  if (db.get(offsetKey) == null) {
    writeStatusToDB(PersistedStatus(Offset(0)))
  }

  def close() = db.close()

  def get: Either[String, PersistedStatus] = Try {
    val bytes = db.get(offsetKey)
    val serializedContent = new String(bytes, StandardCharsets.UTF_8)
    decode[PersistedStatus](serializedContent)
  } match {
    case Success(status) => status.left
      .map(circeError => circeError.getLocalizedMessage)
      .flatMap(result => Right(result))
    case Failure(error) => Left(error.getLocalizedMessage)
  }

  private[this] def writeStatusToDB(status: PersistedStatus) =
    db.put(offsetKey, bytes(status.asJson.noSpaces))

  def put(status: PersistedStatus): Either[String, PersistedStatus] = {
    Try(writeStatusToDB(status)) match {
      case Success(_) => Right(status)
      case Failure(error) => Left(error.getLocalizedMessage)
    }
  }

  def update(update: PersistedStatus => PersistedStatus): Either[String, PersistedStatus] =
    get.flatMap { status =>
      val updatedStatus = update(status)
      Try(writeStatusToDB(updatedStatus)) match {
        case Success(_) => Right(updatedStatus)
        case Failure(error) => Left(error.getLocalizedMessage)
      }
  }
}
