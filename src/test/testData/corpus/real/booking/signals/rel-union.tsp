// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

import "@typespec/json-schema";
import "./booking-created.tsp";
import "./booking-voided.tsp";

using TypeSpec.JsonSchema;

namespace Acme.Ex.Core.Booking.Signals;

/** (doc) */
@jsonSchema
union BookingRelevant {
  created: BookingCreated,
  voided: BookingCancelled,
}
