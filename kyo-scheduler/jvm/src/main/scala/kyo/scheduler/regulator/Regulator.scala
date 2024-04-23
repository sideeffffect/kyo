package kyo.scheduler.regulator

import Regulator.*
import kyo.scheduler.InternalTimer
import kyo.scheduler.util.*
import kyo.stats.internal.MetricReceiver
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal

abstract class Regulator(
    loadAvg: () => Double,
    timer: InternalTimer,
    config: Config
):
    import config.*

    private var step         = 0
    private val measurements = MovingStdDev(collectWindow)

    protected def probe(): Unit
    protected def update(diff: Int): Unit

    protected def measure(v: Long): Unit =
        stats.measurements.observe(v.toDouble)
        synchronized(measurements.observe(v))

    private val collectTask =
        timer.schedule(collectInterval)(collect())

    private val regulateTask =
        timer.schedule(regulateInterval)(adjust())

    final private def collect(): Unit =
        try
            if loadAvg() < loadAvgTarget then
                measure(0)
            else
                stats.probes.inc()
                probe()
            end if
        catch
            case ex if NonFatal(ex) =>
                log.error(s"🙈 !!Kyo Scheduler Bug!! ${getClass.getSimpleName()} regulator's probe collection has failed.", ex)
    end collect

    final private def adjust() =
        try
            val jitter = synchronized(measurements.dev())
            val load   = loadAvg()
            if jitter > jitterUpperThreshold then
                if step < 0 then step -= 1
                else step = -1
            else if jitter < jitterLowerThreshold && load >= loadAvgTarget then
                if step > 0 then step += 1
                else step = 1
            else
                step = 0
            end if
            if step != 0 then
                val delta = (step.sign * Math.pow(step.abs, stepExp)).toInt
                stats.updates.observe(delta)
                update(delta)
            else
                stats.updates.observe(0)
            end if
            stats.jitter.observe(jitter)
            stats.loadavg.observe(load)
        catch
            case ex if NonFatal(ex) =>
                log.error(s"🙈 !!Kyo Scheduler Bug!! ${getClass.getSimpleName()} regulator's adjustment has failed.", ex)
        end try
    end adjust

    def stop(): Unit =
        collectTask.cancel()
        regulateTask.cancel()
        ()
    end stop

    protected val statsScope = kyo.scheduler.statsScope("regulator", getClass.getSimpleName())

    private object stats:
        val receiver     = MetricReceiver.get
        val collects     = receiver.counter(statsScope, "collects")
        val adjusts      = receiver.counter(statsScope, "adjusts")
        val probes       = receiver.counter(statsScope, "probes")
        val loadavg      = receiver.histogram(statsScope, "loadavg")
        val measurements = receiver.histogram(statsScope, "measurements")
        val updates      = receiver.histogram(statsScope, "updates")
        val jitter       = receiver.histogram(statsScope, "jitter")
    end stats

end Regulator

object Regulator:
    private[Regulator] val log = LoggerFactory.getLogger(getClass)
