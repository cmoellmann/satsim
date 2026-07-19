package org.satsim.pus.st3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.PacketDecodeException.Reason;

// Untraced unit tests (engineering hygiene, SDP §5): structural failure paths
// for the ST[3] application data codecs. Semantic validation (SID domains,
// unknown parameter IDs, minimum interval) is deliberately not exercised here
// - it is simulator policy (ICD §9.1/§10.2), not this codec's concern.
class HkCodecsTest {

  // --- HkParameter ---

  @Test
  void hkParameterExposesIdAndOctets() {
    assertEquals(0x0001, HkParameter.P001.id());
    assertEquals(4, HkParameter.P001.octets());
    assertEquals(0x0002, HkParameter.P002.id());
    assertEquals(4, HkParameter.P002.octets());
    assertEquals(0x0003, HkParameter.P003.id());
    assertEquals(2, HkParameter.P003.octets());
  }

  @Test
  void hkParameterLookupByIdIsTotal() {
    assertEquals(Optional.of(HkParameter.P001), HkParameter.byId(0x0001));
    assertEquals(Optional.of(HkParameter.P002), HkParameter.byId(0x0002));
    assertEquals(Optional.of(HkParameter.P003), HkParameter.byId(0x0003));
    // Unknown parameter IDs must be representable (ICD §9.1: simulator policy).
    assertTrue(HkParameter.byId(0x0004).isEmpty());
    assertFalse(HkParameter.byId(0).isPresent());
  }

  // --- HkCreateRequest (TC(3,1), ICD §9.2) ---

  @Test
  void createRequestEncodeMatchesLayout() {
    HkCreateRequest request = new HkCreateRequest(2, 5000L, List.of(1, 3));
    assertArrayEquals(
        new byte[] {0x00, 0x02, 0x00, 0x00, 0x13, (byte) 0x88, 0x00, 0x02, 0x00, 0x01, 0x00, 0x03},
        request.encode());
  }

  @Test
  void createRequestEncodeDecodeRoundTrip() throws PacketDecodeException {
    HkCreateRequest request = new HkCreateRequest(2, 5000L, List.of(1, 3));
    byte[] encoded = request.encode();
    assertEquals(request, HkCreateRequest.decode(encoded));
  }

  @Test
  void createRequestDecodeRejectsTooShort() {
    // Fewer than 8 octets: cannot even hold SID + interval + N1.
    PacketDecodeException ex =
        assertThrows(PacketDecodeException.class, () -> HkCreateRequest.decode(new byte[7]));
    assertEquals(Reason.TOO_SHORT, ex.reason());
  }

  @Test
  void createRequestDecodeRejectsN1OutOfRange() {
    byte[] n1Zero = {0x00, 0x02, 0x00, 0x00, 0x13, (byte) 0x88, 0x00, 0x00};
    PacketDecodeException ex1 =
        assertThrows(PacketDecodeException.class, () -> HkCreateRequest.decode(n1Zero));
    assertEquals(Reason.FIELD_OUT_OF_RANGE, ex1.reason());

    byte[] n1TooLarge = {0x00, 0x02, 0x00, 0x00, 0x13, (byte) 0x88, 0x00, 0x11};
    PacketDecodeException ex2 =
        assertThrows(PacketDecodeException.class, () -> HkCreateRequest.decode(n1TooLarge));
    assertEquals(Reason.FIELD_OUT_OF_RANGE, ex2.reason());
  }

  @Test
  void createRequestDecodeRejectsTruncatedParameterList() {
    // N1 = 2 declared, but only one parameter ID (2 octets) follows.
    byte[] truncated = {0x00, 0x02, 0x00, 0x00, 0x13, (byte) 0x88, 0x00, 0x02, 0x00, 0x01};
    PacketDecodeException ex =
        assertThrows(PacketDecodeException.class, () -> HkCreateRequest.decode(truncated));
    assertEquals(Reason.LENGTH_MISMATCH, ex.reason());
  }

  @Test
  void createRequestDecodeRejectsOversizedInput() {
    HkCreateRequest request = new HkCreateRequest(2, 5000L, List.of(1, 3));
    byte[] oversized = Arrays.copyOf(request.encode(), request.encode().length + 1);
    PacketDecodeException ex =
        assertThrows(PacketDecodeException.class, () -> HkCreateRequest.decode(oversized));
    assertEquals(Reason.LENGTH_MISMATCH, ex.reason());
  }

  @Test
  void createRequestConstructorRejectsOutOfRangeFields() {
    assertThrows(IllegalArgumentException.class, () -> new HkCreateRequest(-1, 5000L, List.of(1)));
    assertThrows(IllegalArgumentException.class, () -> new HkCreateRequest(65536, 5000L, List.of(1)));
    assertThrows(IllegalArgumentException.class, () -> new HkCreateRequest(2, -1L, List.of(1)));
    assertThrows(IllegalArgumentException.class, () -> new HkCreateRequest(2, 0x1_0000_0000L, List.of(1)));
    assertThrows(IllegalArgumentException.class, () -> new HkCreateRequest(2, 5000L, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new HkCreateRequest(2, 5000L, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)));
  }

  @Test
  void createRequestParameterIdsAreDefensivelyCopied() {
    List<Integer> mutable = new ArrayList<>(List.of(1, 3));
    HkCreateRequest request = new HkCreateRequest(2, 5000L, mutable);
    mutable.add(2);
    assertEquals(List.of(1, 3), request.parameterIds());
  }

  // --- HkSidList (TC(3,5)/TC(3,7), ICD §9.3) ---

  @Test
  void sidListEncodeMatchesLayout() {
    HkSidList sidList = new HkSidList(List.of(2));
    assertArrayEquals(new byte[] {0x00, 0x01, 0x00, 0x02}, sidList.encode());
  }

  @Test
  void sidListEncodeDecodeRoundTrip() throws PacketDecodeException {
    HkSidList sidList = new HkSidList(List.of(2));
    assertEquals(sidList, HkSidList.decode(sidList.encode()));
  }

  @Test
  void sidListDecodeRejectsTooShort() {
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> HkSidList.decode(new byte[1]));
    assertEquals(Reason.TOO_SHORT, ex.reason());
  }

  @Test
  void sidListDecodeRejectsNOutOfRange() {
    byte[] nZero = {0x00, 0x00};
    PacketDecodeException ex1 = assertThrows(PacketDecodeException.class, () -> HkSidList.decode(nZero));
    assertEquals(Reason.FIELD_OUT_OF_RANGE, ex1.reason());

    byte[] nTooLarge = {0x00, 0x11};
    PacketDecodeException ex2 = assertThrows(PacketDecodeException.class, () -> HkSidList.decode(nTooLarge));
    assertEquals(Reason.FIELD_OUT_OF_RANGE, ex2.reason());
  }

  @Test
  void sidListDecodeRejectsTruncatedSidList() {
    // N = 2 declared, but only one SID follows.
    byte[] truncated = {0x00, 0x02, 0x00, 0x02};
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> HkSidList.decode(truncated));
    assertEquals(Reason.LENGTH_MISMATCH, ex.reason());
  }

  @Test
  void sidListConstructorRejectsOutOfRangeFields() {
    assertThrows(IllegalArgumentException.class, () -> new HkSidList(List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new HkSidList(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)));
    assertThrows(IllegalArgumentException.class, () -> new HkSidList(List.of(-1)));
    assertThrows(IllegalArgumentException.class, () -> new HkSidList(List.of(65536)));
  }

  @Test
  void sidListSidsAreDefensivelyCopied() {
    List<Integer> mutable = new ArrayList<>(List.of(2));
    HkSidList sidList = new HkSidList(mutable);
    mutable.add(3);
    assertEquals(List.of(2), sidList.sids());
  }

  // --- HkReport (TM(3,25), ICD §9.4) ---

  @Test
  void reportEncodeMatchesLayout() {
    List<HkParameter> order = List.of(HkParameter.P001, HkParameter.P002, HkParameter.P003);
    HkReport report = new HkReport(1, List.of(0L, 0L, 3520L));
    assertArrayEquals(
        new byte[] {0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, (byte) 0xC0},
        report.encode(order));
  }

  @Test
  void reportEncodeDecodeRoundTrip() throws PacketDecodeException {
    List<HkParameter> order = List.of(HkParameter.P001, HkParameter.P002, HkParameter.P003);
    HkReport report = new HkReport(1, List.of(0L, 0L, 3520L));
    byte[] encoded = report.encode(order);
    assertEquals(report, HkReport.decode(encoded, order));
  }

  @Test
  void reportDecodeRejectsLengthMismatch() {
    List<HkParameter> order = List.of(HkParameter.P001, HkParameter.P002, HkParameter.P003);
    // One octet short of the structure-derived length (2 + 4 + 4 + 2 = 12).
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> HkReport.decode(new byte[11], order));
    assertEquals(Reason.LENGTH_MISMATCH, ex.reason());

    PacketDecodeException ex2 = assertThrows(PacketDecodeException.class, () -> HkReport.decode(new byte[13], order));
    assertEquals(Reason.LENGTH_MISMATCH, ex2.reason());
  }

  @Test
  void reportEncodeRejectsOrderValuesSizeMismatch() {
    HkReport report = new HkReport(1, List.of(0L, 0L, 3520L));
    assertThrows(IllegalArgumentException.class, () -> report.encode(List.of(HkParameter.P001)));
  }

  @Test
  void reportEncodeRejectsValueWiderThanParameter() {
    // 0x10000 is a valid uint32 value but does not fit the uint16 slot of P003.
    HkReport report = new HkReport(1, List.of(0x1_0000L));
    assertThrows(IllegalArgumentException.class, () -> report.encode(List.of(HkParameter.P003)));
  }

  @Test
  void reportConstructorRejectsOutOfRangeFields() {
    assertThrows(IllegalArgumentException.class, () -> new HkReport(-1, List.of(0L)));
    assertThrows(IllegalArgumentException.class, () -> new HkReport(65536, List.of(0L)));
    assertThrows(IllegalArgumentException.class, () -> new HkReport(1, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new HkReport(1, List.of(-1L)));
    assertThrows(IllegalArgumentException.class, () -> new HkReport(1, List.of(0x1_0000_0000L)));
  }

  @Test
  void reportValuesAreDefensivelyCopied() {
    List<Long> mutable = new ArrayList<>(List.of(0L, 0L, 3520L));
    HkReport report = new HkReport(1, mutable);
    mutable.set(0, 99L);
    assertEquals(List.of(0L, 0L, 3520L), report.values());
  }
}
