// case class OffsetEvent(offset: Offset)
// case class OffsetState(offsets: List[OffsetEvent] = Nil) {
//   def updated(evt: OffsetEvent): OffsetState = copy(evt :: offsets)
//   def size: Int = offsets.length
//   override def toString: String = offsets.reverse.toString
// }

// case class OffsetCmd(offset: Offset)
// class OffsetPersistorActor extends PersistentActor with LazyLogging {
//   override def persistenceId = "offset-persistance"

//   private[this] val snapShotInterval = 1000

//   var state = OffsetState()

//   private[this] def updateState(event: OffsetEvent): Unit =
//     state = state.updated(event)

//   private[this] def numEvents: Int =
//     state.size

//   val receiveRecover: Receive = {
//     case SnapshotOffer(_, snapshot: OffsetState) => state = snapshot
//   }

//   val receiveCommand: Receive = {
//     case command@OffsetCmd(offset) =>
//       logger.debug(s"command $command received")
//       persist(OffsetEvent(offset)) { event =>
//         deleteMessages(lastSequenceNr)
//         updateState(event)
//         context.system.eventStream.publish(event)
//         if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
//           saveSnapshot(state)
//       }
//       context.sender ! command
//   }

//   val logSnapshotResult: Receive = SnapshotHelper.logSnapshotResult
// }


