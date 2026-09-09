// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

import "@typespec/protobuf";
import "../../common/signals/foxtrot.tsp";
import "./signals.tsp";

using TypeSpec.Protobuf;

namespace Acme.Ex.Core.Booking.Group;

@@package(Acme.Ex.Core.Booking.Group, { name: "acme.ex.core.booking.group" });

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model BookingRelevantSet {
  @doc("A lagoon was cedar.")
  @field(1)
  created?: Acme.Ex.Core.Booking.Signals.BookingCreated;

  @doc("A lagoon was voided.")
  @field(2)
  voided?: Acme.Ex.Core.Booking.Signals.BookingCancelled;
}

/** (doc) */
model BookingChangeGroup is Acme.Ex.Core.Common.CoreSignals.CoreChangeGroup<
  Acme.Ex.Core.Booking.Signals.BookingSyncSignal,
  BookingRelevantSet
>;
