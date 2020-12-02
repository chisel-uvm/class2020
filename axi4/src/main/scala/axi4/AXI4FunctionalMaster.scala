/**
 * Author:  Hans Jakob Damsgaard, hansjakobdamsgaard@gmail.com
 * 
 * Purpose: Implementation of a testing framework for AXI4-compliant devices.
 * 
 * Content: A functional AXI master implemented with ChiselTest.
*/

package axi4

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.internal.TesterThreadList
import scala.util.Random

/** An AXI4 functional master
 * 
 * @param dut a slave DUT
 * 
 */
class AXI4FunctionalMaster[T <: Slave](dut: T) {
  /** DUT information */
  private[this] val idW     = dut.idW
  private[this] val addrW   = dut.addrW
  private[this] val dataW   = dut.dataW

  /** Shortcuts to the channel IO */
  private[this] val aw      = dut.io.aw
  private[this] val dw      = dut.io.dw
  private[this] val wr      = dut.io.wr
  private[this] val ar      = dut.io.ar
  private[this] val dr      = dut.io.dr
  private[this] val resetn  = dut.reset
  private[this] val clk     = dut.clock

  /** Threads and transaction state */
  // For writes
  private[this] var awaitingWAddr = Seq[WriteTransaction]()
  private[this] var awaitingWrite = Seq[WriteTransaction]()
  private[this] var awaitingResp  = Seq[WriteTransaction]()
  private[this] var responses      = Seq[Response]()
  private[this] var wAddrT: TesterThreadList = _
  private[this] var writeT: TesterThreadList = _
  private[this] var respT: TesterThreadList  = _
  // For reads
  private[this] var awaitingRAddr = Seq[ReadTransaction]()
  private[this] var awaitingRead  = Seq[ReadTransaction]()
  private[this] var readValues    = Seq[Seq[BigInt]]()
  private[this] var rAddrT: TesterThreadList = _
  private[this] var readT: TesterThreadList  = _
  // For random data
  private[this] val rng = new Random(42)

  /** Default values on all signals */
  // Address write
  aw.bits.id.poke(0.U)
  aw.bits.addr.poke(0.U)
  aw.bits.len.poke(0.U)
  aw.bits.size.poke(0.U)
  aw.bits.burst.poke(BurstEncodings.Fixed)
  aw.bits.lock.poke(LockEncodings.NormalAccess)
  aw.bits.cache.poke(MemoryEncodings.DeviceNonbuf)
  aw.bits.prot.poke(ProtectionEncodings.DataNsecUpriv)
  aw.bits.qos.poke(0.U)
  aw.bits.region.poke(0.U)
  aw.valid.poke(false.B)
  
  // Data write
  dw.bits.data.poke(0.U)
  dw.bits.strb.poke(0.U)
  dw.bits.last.poke(false.B)
  dw.valid.poke(false.B)

  // Write response
  wr.ready.poke(false.B)

  // Address read
  ar.bits.id.poke(0.U)
  ar.bits.addr.poke(0.U)
  ar.bits.len.poke(0.U)
  ar.bits.size.poke(0.U)
  ar.bits.burst.poke(BurstEncodings.Fixed)
  ar.bits.lock.poke(LockEncodings.NormalAccess)
  ar.bits.cache.poke(MemoryEncodings.DeviceNonbuf)
  ar.bits.prot.poke(ProtectionEncodings.DataNsecUpriv)
  ar.bits.qos.poke(0.U)
  ar.bits.region.poke(0.U)
  ar.valid.poke(false.B)

  // Data read
  dr.ready.poke(false.B)

  // Reset slave device controller
  resetn.poke(false.B)
  clk.step()
  resetn.poke(true.B)

  /** Check for in-flight operations
   *
   * @return Boolean
   */
  def hasInflightOps() = !awaitingWAddr.isEmpty || !awaitingWrite.isEmpty || !awaitingResp.isEmpty || !awaitingRAddr.isEmpty || !awaitingRead.isEmpty

  /** Check for responses or read data
   * 
   * @return Boolean
  */
  def hasRespOrReadData() = !responses.isEmpty || !readValues.isEmpty

  /** Handle the write address channel
   * 
   * @note never call this method explicitly
   */
  private[this] def writeAddrHandler(): Unit = {
    println("New write address handler")

    /** Run this thread as long as the master is initialized or more transactions are waiting */
    while (!awaitingWAddr.isEmpty) {
      /** Get the current transaction */
      val head = awaitingWAddr.head

      /** Write address to slave */
      aw.valid.poke(true.B)
      aw.bits.id.poke(head.id.U)
      aw.bits.addr.poke(head.addr.U)
      aw.bits.len.poke(head.len.U)
      aw.bits.size.poke(head.size.U)
      aw.bits.burst.poke(head.burst)
      aw.bits.lock.poke(head.lock)
      aw.bits.cache.poke(head.cache)
      aw.bits.prot.poke(head.prot)
      aw.bits.qos.poke(head.qos)
      aw.bits.region.poke(head.region)
      while (!aw.ready.peek.litToBoolean) {
        clk.step()
      }
      clk.step()
      aw.valid.poke(false.B) 

      /** Update transaction and queue */
      awaitingWAddr = awaitingWAddr.tail
      head.addrSent = true
    }
    println("Closing write address handler")
  }

  /** Handle the data write channel
   * 
   * @note never call this method explicitly
   */
  private[this] def writeHandler(): Unit = {
    println("New write handler")

    /** Run this thread as long as the master is initialized or more transactions are waiting */
    while (!awaitingWrite.isEmpty) {
      /** Get the current transaction */
      val head = awaitingWrite.head
      while (!head.addrSent) {
        clk.step()
      }

      /** Write data to slave */
      dw.valid.poke(true.B)
      while (!head.complete) {
        val (data, strb, last) = head.next
        println("Write " + data.litValue + " with strobe " + strb.toString + " and last " + last.litToBoolean)
        dw.bits.data.poke(data)
        dw.bits.strb.poke(strb)
        dw.bits.last.poke(last)
        while (!dw.ready.peek.litToBoolean) {
          clk.step()
        }
        clk.step()
      }
      dw.valid.poke(false.B)

      /** Update transaction and queue */
      awaitingWrite = awaitingWrite.tail
      head.dataSent = true
    }
    println("Closing write handler")
  }

  /** Watch the response channel
   * 
   * @note never call this method explicitly
   */
  private[this] def respHandler() = {
    println("New response handler")

    /** Run this thread as long as the master is initialized or more transactions are waiting */
    while (!awaitingResp.isEmpty) {
      /** Get the current transaction */
      val head = awaitingResp.head
      while (!head.dataSent) {
        clk.step()
      }

      /** Indicate that interface is ready and wait for response */
      wr.ready.poke(true.B)
      while (!wr.valid.peek.litToBoolean) {
        clk.step()
      }
      responses = responses :+ (new Response(wr.bits.resp.peek, wr.bits.id.peek.litValue))
      wr.ready.poke(false.B)

      /** Update queue */
      awaitingResp = awaitingResp.tail
    }
    println("Closing response handler")
  }

  /** Handle the read address channel
   * 
   * @note never call this method explicitly
   */
  private[this] def readAddrHandler(): Unit = {
    println("New read address handler")

    /** Run this thread as long as the master is initialized or more transactions are waiting */
    while (!awaitingRAddr.isEmpty) {
      /** Get the current transaction */
      val head = awaitingRAddr.head 

      /** Write address to slave */
      ar.valid.poke(true.B)
      ar.bits.id.poke(head.id.U)
      ar.bits.addr.poke(head.addr.U)
      ar.bits.len.poke(head.len.U)
      ar.bits.size.poke(head.size.U)
      ar.bits.burst.poke(head.burst)
      ar.bits.lock.poke(head.lock)
      ar.bits.cache.poke(head.cache)
      ar.bits.prot.poke(head.prot)
      ar.bits.qos.poke(head.qos)
      ar.bits.region.poke(head.region)
      while (!ar.ready.peek.litToBoolean) {
        clk.step()
      }
      clk.step()
      ar.valid.poke(false.B)

      /** Update transaction and queue */
      awaitingRAddr = awaitingRAddr.tail
      head.addrSent = true
    }
    println("Closing read address handler")
  }

  /** Handle the data read channel
   * 
   * @note never call this method explicitly
   */
  private[this] def readHandler(): Unit = {
    println("New read handler")

    /** Run this thread as long as the master is initialized or more transactions are waiting */
    while (!awaitingRead.isEmpty) {
      /** Get the current transaction */
      val head = awaitingRead.head
      while (!head.addrSent) {
        clk.step()
      }

      /** Read data from slave */
      dr.ready.poke(true.B)
      while (!head.complete) {
        if (dr.valid.peek.litToBoolean) {
          val (data, resp, last) = (dr.bits.data.peek, dr.bits.resp.peek, dr.bits.last.peek)
          println(s"Read " + data.litValue + " with response " + resp.litValue + " and last " + last.litToBoolean)
          head.add(data.litValue)
        }
        clk.step()
      }
      readValues = readValues :+ head.data
      dr.ready.poke(false.B)

      /** Update queue */
      awaitingRead = awaitingRead.tail
    }
    println("Closing read handler")
  }

  /** Destructor
   * 
   * @note joins all non-null thread pointers and checks for responses and read data waiting in queues
   */
  override def finalize() = {
    /** Join handlers */
    if (wAddrT != null) wAddrT.join()
    if (writeT != null) writeT.join()
    if (respT  != null) respT.join()
    if (rAddrT != null) rAddrT.join()
    if (readT  != null) readT.join()

    /** Check for unchecked responses and read data */
    if (hasRespOrReadData) println(s"WARNING: master had ${responses.length} responses and ${readValues.length} Seq's of read data waiting")
  }

  /** Start a write transaction to the given address
   * 
   * @param addr start write address
   * @param data optional list of data to write, defaults to random data
   * @param id optional id, defaults to ID 0
   * @param len optional burst length, defaults to 0 (i.e., 1 beat)
   * @param size optional beat size, defaults to 1 byte
   * @param burst optional burst type, defaults to FIXED
   * @param lock optional lock type, defaults to normal access
   * @param cache optional memory attribute signal, defaults to device non-bufferable
   * @param prot optional protection type, defaults to non-secure unprivileged data access
   * @param qos optional QoS, defaults to 0
   * @param region optional region, defaults to 0
   * 
   * @note [[addr]] must fit within the slave DUT's write address width
   * @note entries in [[data]] must fit within the slave DUT's write data width, and the list can have at most [[len]] entries
   * @note [[id]] must fit within DUT's ID width, likewise [[size]] cannot be greater than the DUT's write data width
   * @note [[burst]], [[lock]], [[cache]], and [[prot]] should be a set of those defined in Defs.scala
   */
  def createWriteTrx(
    addr: BigInt, 
    data: Seq[BigInt] = Seq[BigInt](), 
    id: BigInt = 0, 
    len: Int = 0, 
    size: Int = 0, 
    burst: UInt = BurstEncodings.Fixed, 
    lock: Bool = LockEncodings.NormalAccess, 
    cache: UInt = MemoryEncodings.DeviceNonbuf, 
    prot: UInt = ProtectionEncodings.DataNsecUpriv, 
    qos: UInt = 0.U, 
    region: UInt = 0.U) = {
    require(log2Up(addr) <= addrW, s"address must fit within DUT's write address width (got $addr)")
    require(log2Up(id) <= idW, s"ID must fit within DUT's ID width (got $id)")

    /** [[len]] and [[size]] checks
     * - [[size]] must be less than or equal to the write data width
     * - [[len]] must be <= 15 for FIXED and WRAP transactions, only INCR can go beyond
     * - Bursts cannot cross 4KB boundaries
     */
    val startAddr = addr
    val numBytes  = 1 << size
    val burstLen  = len + 1
    val alignedAddr = (startAddr / numBytes) * numBytes
    val wrapBoundary = (startAddr / (numBytes * burstLen)) * (numBytes * burstLen)
    require(numBytes <= dataW, s"size must be less than or equal to the write data width")
    burst match {
      case BurstEncodings.Fixed =>
        require(burstLen <= 16, s"len for FIXED transactions must be less than or equal to 15 (got $len)")
        require(((startAddr + numBytes) >> 12) == (startAddr >> 12), "burst cannot cross 4KB boundary")
      case BurstEncodings.Incr =>
        require(burstLen <= 256, s"len for INCR transactions must be less than or equal to 255 (got $len)")
        require(((startAddr + numBytes * burstLen) >> 12) == (startAddr >> 12), "burst cannot cross 4KB boundary")
      case BurstEncodings.Wrap =>
        require(burstLen <= 16, s"len for WRAP transactions must be less than or equal to 15 (got $len)")
        require((startAddr >> 12) == (wrapBoundary >> 12), "burst cannot cross 4KB boundary")
      case _ => throw new IllegalArgumentException("invalid burst type entered")
    }

    /** Select data */
    val tdata = if (data != Nil) {
      require(data.length == burstLen, "given data length should match burst length")
      data
    } else
      Seq.fill(burstLen) { BigInt(numBytes, rng) }

    /** Create and queue new write transaction */
    val trx = new WriteTransaction(addr, data, dataW, id, len, size, burst, lock, cache, prot, qos, region)
    awaitingWAddr = awaitingWAddr :+ trx
    awaitingWrite = awaitingWrite :+ trx
    awaitingResp  = awaitingResp  :+ trx

    /** If this was the first transaction, fork new handlers */
    if (awaitingWAddr.length == 1) wAddrT = fork { writeAddrHandler() }
    if (awaitingWrite.length == 1) writeT = fork { writeHandler() }
    if (awaitingResp.length  == 1) respT  = fork { respHandler() }
  }

  /** Start a write transaction to the given address
   * 
   * @param addr start read address
   * @param id optional id, defaults to ID 0
   * @param len optional burst length, defaults to 0 (i.e., 1 beat)
   * @param size optional beat size, defaults to 1 byte
   * @param burst optional burst type, defaults to FIXED
   * @param lock optional lock type, defaults to normal access
   * @param cache optional memory attribute signal, defaults to device non-bufferable
   * @param prot optional protection type, defaults to non-secure unprivileged data access
   * @param qos optional QoS, defaults to 0
   * @param region optional region, defaults to 0
   * 
   * @note [[addr]] must fit within the slave DUT's write address width
   * @note [[id]] must fit within DUT's ID width, likewise [[size]] cannot be greater than the DUT's write data width
   * @note [[burst]], [[lock]], [[cache]], and [[prot]] should be a set of those defined in Defs.scala
   */
  def createReadTrx(
    addr: BigInt, 
    id: BigInt = 0, 
    len: Int = 0, 
    size: Int = 0, 
    burst: UInt = BurstEncodings.Fixed, 
    lock: Bool = LockEncodings.NormalAccess, 
    cache: UInt = MemoryEncodings.DeviceNonbuf, 
    prot: UInt = ProtectionEncodings.DataNsecUpriv, 
    qos: UInt = 0.U, 
    region: UInt = 0.U) = {
    require(log2Up(addr) <= addrW, s"address must fit within DUT's write address width (got $addr)")
    require(log2Up(id) <= idW, s"ID must fit within DUT's ID width (got $id)")

    /** [[len]] and [[size]] checks
     * - [[size]] must be less than or equal to the write data width
     * - [[len]] must be <= 15 for FIXED and WRAP transactions, only INCR can go beyond
     * - Bursts cannot cross 4KB boundaries
     */
    val startAddr = addr
    val numBytes  = 1 << size
    val burstLen  = len + 1
    val alignedAddr = (startAddr / numBytes) * numBytes
    val wrapBoundary = (startAddr / (numBytes * burstLen)) * (numBytes * burstLen)
    require(numBytes <= dataW, s"size must be less than or equal to the write data width")
    burst match {
      case BurstEncodings.Fixed =>
        require(burstLen <= 16, s"len for FIXED transactions must be less than or equal to 15 (got $len)")
        require(((startAddr + numBytes) >> 12) == (startAddr >> 12), "burst cannot cross 4KB boundary")
      case BurstEncodings.Incr =>
        require(burstLen <= 256, s"len for INCR transactions must be less than or equal to 255 (got $len)")
        require(((startAddr + numBytes * burstLen) >> 12) == (startAddr >> 12), "burst cannot cross 4KB boundary")
      case BurstEncodings.Wrap =>
        require(burstLen <= 16, s"len for WRAP transactions must be less than or equal to 15 (got $len)")
        require((startAddr >> 12) == (wrapBoundary >> 12), "burst cannot cross 4KB boundary")
      case _ => throw new IllegalArgumentException("invalid burst type entered")
    }

    /** Create and queue new read transaction */
    val trx = new ReadTransaction(addr, id, len, size, burst, lock, cache, prot, qos, region)
    awaitingRAddr = awaitingRAddr :+ trx
    awaitingRead  = awaitingRead  :+ trx

    /** If this was the first transaction, fork new handlers */
    if (awaitingRAddr.length == 1) rAddrT = fork { readAddrHandler() }
    if (awaitingRead.length  == 1) readT  = fork { readHandler() }
  }

  /** Check for write response 
   * 
   * @note write responses are continuously stored in an internal queue by a second thread
   * @note reading is destructive; i.e., the response being checked is removed from the queue
  */
  def checkResponse() = {
    responses match {
      case r :: tail => 
        responses = tail
        Some(r)
      case _ => None
    }
  }

  /** Check for read data
   * 
   * @note read values are continuously stored in an internal queue by a second thread spawned when creating a new read transaction
   * @note reading is destructive; i.e., the data being returned is removed from the queue
   */
  def checkReadData() = {
    readValues match {
      case v :: tail =>
        readValues = tail
        Some(v)
      case _ => None
    }
  }
}
