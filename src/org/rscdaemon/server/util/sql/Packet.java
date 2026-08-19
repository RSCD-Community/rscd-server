package org.rscdaemon.server.util.sql;

/*
 * One MySQL protocol packet body, with the length and sequence header already
 * stripped. The type tests below are the standard first-byte discriminators.
 */
final class Packet {

   private final byte[] body;

   Packet(byte[] body) {
      this.body = body;
   }

   byte[] body() {
      return this.body;
   }

   int length() {
      return this.body.length;
   }

   int type() {
      return this.body.length == 0 ? -1 : this.body[0] & 0xFF;
   }

   boolean isOk() {
      return type() == 0x00;
   }

   boolean isErr() {
      return type() == 0xFF;
   }

   /*
    * An EOF packet is 0xFE AND shorter than 9 bytes. The length test is not
    * optional: 0xFE is also the marker for an 8-byte length-encoded integer, so
    * a row whose first column is long enough would otherwise be mistaken for
    * end-of-result and truncate the result set.
    */
   boolean isEof() {
      return type() == 0xFE && this.body.length < 9;
   }

   boolean isAuthSwitch() {
      return type() == 0xFE && this.body.length >= 9;
   }
}
