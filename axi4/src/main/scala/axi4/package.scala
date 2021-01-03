/**
 * Author:  Hans Jakob Damsgaard, hansjakobdamsgaard@gmail.com
 * 
 * Purpose: Implementation of a testing framework for AXI4-compliant devices.
 * 
 * Content: Package object for full AXI4.
*/

import chisel3._

package object axi4 {
  /** AXI4-Lite master
   * 
   * An empty class representing an AXI master
   *
   * @param addrW the width of the address signals in bits
   * @param dataW the width of the data read/write signals in bits
   */
  abstract class LiteMaster(val addrW: Int, val dataW: Int) extends Module {
    val io = IO(new MasterInterfaceLite(addrW, dataW))
  }

  /** AXI4 full master
   * 
   * An empty class representing an AXI master
   *
   * @param addrW the width of the address signals in bits
   * @param dataW the width of the data read/write signals in bits
   * @param idW the width of the ID signals in bits, defaults to 0
   * @param userW the width of the user signals in bits, defaults to 0
   */
  abstract class Master(val addrW: Int, val dataW: Int, val idW: Int = 0, val userW: Int = 0) extends Module {
    val io = IO(new MasterInterface(addrW, dataW, idW, userW))
  }

  /** AXI4-Lite slave
   * 
   * An empty class representing an AXI slave
   *
   * @param addrW the width of the address signals in bits
   * @param dataW the width of the data read/write signals in bits
   */
  abstract class LiteSlave(val addrW: Int, val dataW: Int) extends Module {
    val io = IO(new SlaveInterfaceLite(addrW, dataW))
  }

  /** AXI4 full slave
   * 
   * An empty class representing an AXI slave
   *
   * @param addrW the width of the address signals in bits
   * @param dataW the width of the data read/write signals in bits
   * @param idW the width of the ID signals in bits, defaults to 0
   * @param userW the width of the user signals in bits, defaults to 0
   */
  abstract class Slave(val addrW: Int, val dataW: Int, val idW: Int = 0, val userW: Int = 0) extends Module {
    val io = IO(new SlaveInterface(addrW, dataW, idW, userW))
  }

  /** AXI4 burst encodings */
  object BurstEncodings {
    val Fixed             = "b00".U
    val Incr              = "b01".U
    val Wrap              = "b10".U
  }
  
  /** AXI lock encodings */
  object LockEncodings {
    val NormalAccess     = false.B
    val ExclusiveAccess  = true.B
  }
  
  /** AXI4 memory encodings */
  object MemoryEncodings {
    val DeviceNonbuf     = "b0000".U
    val DeviceBuf        = "b0001".U
    val NormalNonbuf     = "b0010".U 
    val NormalBuf        = "b0011".U
    val WtNoalloc        = "b0110".U
    val WtReadalloc      = "b0110".U
    val WtWritealloc     = "b1110".U
    val WtRwalloc        = "b1110".U
    val WbNoalloc        = "b0111".U
    val WbReadalloc      = "b0111".U
    val WbWritealloc     = "b1111".U
    val WbRwalloc        = "b1111".U
  }
  
  /** AXI4 protection encodings */
  object ProtectionEncodings {
    val DataSecUpriv    = "b000".U
    val DataSecPriv     = "b001".U
    val DataNsecUpriv   = "b010".U
    val DataNsecPriv    = "b011".U
    val InstrSecUpriv   = "b100".U
    val InstrSecPriv    = "b101".U
    val InstrNsecUpriv  = "b110".U
    val InstrNsecPriv   = "b111".U
  }
  
  /** AXI4 response encodings */
  object ResponseEncodings {
    val Okay              = "b00".U
    val Exokay            = "b01".U
    val Slverr            = "b10".U
    val Decerr            = "b11".U
  }
}
