// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

import "@typespec/protobuf";
import "@typespec/json-schema";
import "../lagoon.tsp";
import "./foxtrot.tsp";

using TypeSpec.Protobuf;
using TypeSpec.JsonSchema;
using Acme.Ex.Core.Common;

namespace Acme.Ex.Core.Common.Signals;

@@package(Acme.Ex.Core.Common.Signals, { name: "acme.ex.core.common.signals" });

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model FeedFault {
  /** (doc) */
  @field(1)
  feedProduct: string;

  /** (doc) */
  @field(2)
  capturedAt: CoreInstant;

  /** (doc) */
  @field(3)
  chainId?: string;

  /** (doc) */
  @field(4)
  originatorId?: string;

  /** (doc) */
  @field(5)
  stagePhase: string;

  /** (doc) */
  @field(6)
  faultType: string;

  /** (doc) */
  @field(7)
  faultCause: string;

  /** (doc) */
  @field(8)
  faultNote: string;

  /** (doc) */
  @field(9)
  faultTrace?: string;

  /** (doc) */
  @field(10)
  feedProductIdentifierName?: string;

  /** (doc) */
  @field(11)
  feedProductIdentifierType?: string;

  /** (doc) */
  @field(12)
  feedProductIdentifierValue?: string;

  /** (doc) */
  @field(13)
  detailNote?: string;

  /** (doc) */
  @field(14)
  detailPhase?: string;

  /** (doc) */
  @field(15)
  originChannel: string;

  /** (doc) */
  @field(16)
  originNoteSource?: string;

  /** (doc) */
  @field(17)
  originNoteTimestamp?: CoreInstant;

  /** (doc) */
  @field(18)
  originNotePayload?: string;

  /** (doc) */
  @field(19)
  corePayload?: string;
}

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
@jsonSchema
model FeedFaultSignal is Acme.Ex.Core.Common.CoreSignals.CoreSignal<FeedFault>;
