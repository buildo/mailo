package mailo

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import mailo.data.MailData
import mailo.http.MailClient
import mailo.persistence.{EmailPersistanceActor, DeadEmailsHandlerActor, SendEmail}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class MailPersistenceError(override val message: String) extends MailError(message)

class AtLeastOnceMailo(
  val data: MailData,
  val client: MailClient,
)(
  implicit
  ec: ExecutionContext,
  conf: Config = ConfigFactory.load(),
  system: ActorSystem = ActorSystem("mailo"),
  enqueueTimeout: Timeout = Timeout(200 milliseconds)
) extends Mailo
    with LazyLogging {
  private[this] val emailSender = new EmailSender(data, client)
  private[this] val emailPersistanceActor = system.actorOf(EmailPersistanceActor.props(emailSender))
  system.actorOf(DeadEmailsHandlerActor.props())

  def send(mail: Mail): Future[Either[MailError, MailResult]] = {
    ask(emailPersistanceActor, SendEmail(mail))
      .map(_ => Right(LocallyQueued))
  }
}
