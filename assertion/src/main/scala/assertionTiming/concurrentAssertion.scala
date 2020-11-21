package assertionTiming

import scala.util.control.Breaks._
import org.scalatest._
import chiseltest._
import chisel3._

/** Checks for a condition to be valid in the circuit at all times, or within
  * the specified amount of clock cycles. If the condition evaluates to false,
  * the circuit simulation stops with an error.
  *
  * @param cond condition, assertion fires (simulation fails) when false
  * @param message optional format string to print when assertion fires
  * @param cycles optional amount of clock cycles for which the assertion is 
  * checked, instead of an immediate assertion
  *
  * This object is part of the special course "Verification of Digital Designs"
  * on DTU, autumn semester 2020.
  *
  * @author Victor Alexander Hansen, s194027@student.dtu.dk
  * @author Niels Frederik Flemming Holm Frandsen, s194053@student.dtu.dk
  *
  * Works only with registers
  */

/** assertNever():
  * Checks for the argument condition to not be true in the number of cycles passed
  */
object assertNever {
    def apply[T <: Module](dut: T, cond: () => Boolean, message: String, cycles: Int) = {

        // Assertion for single thread clock cycle 0
        assert(cond(), message)
        fork {
            for (i <- 0 until cycles) {
                assert(!cond(), message)
                dut.clock.step(1)
            }
        }
    }
}

/** assertAlways():
  * Checks for the argument condition to be true in the number of cycles passed
  */
object assertAlways {
    def apply[T <: Module](dut: T, cond: () => Boolean, message: String, cycles: Int) = {

        // Assertion for single thread clock cycle 0
        assert(cond(), message)
        fork {
            for (i <- 1 until cycles) {
                assert(cond(), message)
                dut.clock.step(1)
            }
        }
    }
}

/** assertEventually():
  * Checks for the argument condition to be true just once within the number of
  * clock cycles passed, a liveness property. Fails if the condition is not true
  * at least once within the window of cycles
  *
  * Must be joined
  */
object assertEventually {
    def apply[T <: Module](dut: T, cond: () => Boolean, message: String, cycles: Int) = {

        var i = 1
        // Assertion for single thread clock cycle 0
        assert(cond(), message)

        fork {
            /*for (i <- 0 until cycles) {
                if (cond()) {
                    break
                } else if (i == cycles - 1){
                    assert(false, message)
                }
                dut.clock.step(1)
            }*/
            while (!cond()) {
                if (i == cycles-1) {
                    assert(false, message)
                }
                i += 1
                dut.clock.step(1)
            }
        }
    }
}

/** assertEventuallyAlways():
  * Checks for the argument condition to be true within the number of
  * clock cycles passed, and hold true until the last cycle. Fails if the 
  * condition is not true at least once within the window of cycles, or if
  * condition becomes false after it becomes true.
  *
  * Must be joined
  */
object assertEventuallyAlways {
    def apply[T <: Module](dut: T, cond: () => Boolean, message: String, cycles: Int) = {

        var i = 1
        // Assertion for single thread clock cycle 0
        assert(cond(), message)

        /*for (i <- 0 until cycles) {
            if (cond()) {
                break
            } else {
                // Exception
                assert(false, message)
            }
            k += 1
        }*/

        fork {
            while (!cond()) {
                if (i == cycles-1) {
                    assert(false, message)
                }
                i += 1
                dut.clock.step(1)
            }

            for (j <- 0 until cycles - i) {
                assert(cond(), message)
                dut.clock.step(1)
            }
        }
    }
}

/** assertOneHot():
  * checks if exactly one bit of the expression is high
  */
object assertOneHot {
    def apply(cond: UInt, message: String, cycles: Int) {

        // Assertion for single thread clock cycle 0
        assert(cond(), message)
        for (i <- 0 until cycles) {
            assert(cond())
        }
    }
}