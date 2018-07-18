package mailo.persistence

import akka.NotUsed
import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence._
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.scalalogging.LazyLogging

case class CleanQueue(lastSequenceNr: Long)
case class SendEmail(data: String)
case class EmailEvent(content: String)

object Main extends App with LazyLogging {
  val system = ActorSystem("mail-actor-system")
  val persistentEmailActor = system.actorOf(Props[EmailPersistorActor])
  implicit val mat = ActorMaterializer()(system)
  val storage = new FileRepository("file")
  val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](
    LeveldbReadJournal.Identifier)

  def mailCommands = {
    persistentEmailActor ! SendEmail("data-1")
    Thread sleep 2000
    println("go now!")
    Thread sleep 2000
    persistentEmailActor ! SendEmail("data-2")
    Thread sleep 2000
  }

  def readStuff = {
    val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](
      LeveldbReadJournal.Identifier)

    val src: Source[EventEnvelope, NotUsed] =
      queries.eventsByPersistenceId("sample-id-1", 0L, Long.MaxValue)

    val events: Source[Any, NotUsed] = src.map(_.event)

    events.runForeach(i ⇒ println(i))(mat)
  }

  def readEmails = {
    val source = Source.single(storage.get.getOrElse(throw new Exception("")).offset).flatMapConcat { offset =>
      queries.eventsByPersistenceId("emails-persistence", offset.value, Long.MaxValue)
    }
    val sink = Sink.foreach[EventEnvelope](updateOffset)
    val flow = Flow[EventEnvelope].map(sendEmail)
    val runnable = source.via(flow).toMat(sink)(Keep.right)
    runnable.run()
  }

  def sendEmail(event: EventEnvelope) = {
    println(event)
    event
  }

  def incr(long: Long) = {
    long + 1L
  }

  def updateOffset(event: EventEnvelope) = {
    storage.put(PersistedStatus(Offset(incr(event.sequenceNr))))
  }

  implicit def intToOffset(i: Int): Offset = Offset(i)

  def sendCommands1 = {
    persistentEmailActor ! SendEmail("data-1")
    persistentEmailActor ! SendEmail("data-2")
    persistentEmailActor ! SendEmail("data-3")
    persistentEmailActor ! SendEmail("data-4")
    persistentEmailActor ! SendEmail("data-5")
    persistentEmailActor ! SendEmail("data-6")
  }
  def sendCommands2 = {
    persistentEmailActor ! SendEmail("data-7")
    persistentEmailActor ! SendEmail("data-8")
    persistentEmailActor ! SendEmail("data-9")
    persistentEmailActor ! SendEmail("data-10")
  }

  // sendCommands2
  sendCommands1
  println("everything good...")
  Thread sleep 1000
  println("waiting...")
  Thread sleep 1000
  println("go...")
  readEmails

  Thread sleep 2000

  sendCommands2
  readEmails

  println("hm...")
  readEmails

  Thread sleep 2000
  system terminate
}

case class EmailState(events: List[EmailEvent] = Nil) {
  def updated(evt: EmailEvent): EmailState = copy(evt :: events)
  def size: Int = events.length
  override def toString: String = events.reverse.toString
}

class EmailPersistorActor extends PersistentActor with ActorLogging {
  override def persistenceId = "emails-persistence"

  var state = EmailState()

  private[this] def updateState(event: EmailEvent): Unit =
    state = state.updated(event)

  private[this] def numEvents: Int =
    state.size

  val receiveRecover: Receive = {
    //case email: EmailEvent                      => log.info(email.toString())
    case SnapshotOffer(_, snapshot: EmailState) => state = snapshot
  }

  val snapShotInterval = 1000
  val receiveCommand: Receive = {
    case command@CleanQueue(sequenceNr) =>
      log.info("received event {}", command.toString)
      saveSnapshot(state)
      deleteMessages(sequenceNr)
    case command@SendEmail(data) =>
      log.info("received command {}", command.toString)
      persistAsync(EmailEvent(s"$data-$numEvents")) { event =>
        updateState(event)
        context.system.eventStream.publish(event)
        if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(state)
      }
  }

  val logSnapshotResult: Receive = SnapshotHelper.logSnapshotResult
}

