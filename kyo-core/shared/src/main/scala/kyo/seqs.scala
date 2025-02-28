package kyo

import kyo.core.*

object Seqs:

    def traverse[T, U, S, S2](v: Seq[T])(f: T => U < S2): Seq[U] < (S & S2) =
        collect(v.map(f))

    def traverseUnit[T, U, S](v: Seq[T])(f: T => Unit < S): Unit < S =
        def loop(l: Seq[T]): Unit < S =
            if l.isEmpty then ()
            else f(l.head).andThen(loop(l.tail))
        loop(v)
    end traverseUnit

    def fold[T, U, S](v: Seq[T])(acc: U)(f: (U, T) => U < S): U < S =
        val it = v.iterator
        def loop(acc: U): U < S =
            if !it.hasNext then acc
            else f(acc, it.next()).map(loop(_))
        loop(acc)
    end fold

    def collect[T, S](v: Seq[T < S]): Seq[T] < S =
        v match
            case v: IndexedSeq[T < S] =>
                val r = new Array[Any](v.size).asInstanceOf[Array[T]]
                def loop(i: Int): Seq[T] < S =
                    if i == v.size then
                        r.toIndexedSeq
                    else
                        v(i).map { t =>
                            r(i) = t
                            loop(i + 1)
                        }
                loop(0)
            case _ =>
                val b = Seq.newBuilder[T]
                def loop(v: Seq[T < S]): Seq[T] < S =
                    if v.isEmpty then
                        b.result()
                    else
                        v.head.map { t1 =>
                            b += t1
                            loop(v.tail)
                        }
                    end if
                end loop
                loop(v)
        end match
    end collect

    def collectUnit[T, S](v: Seq[Unit < S]): Unit < S =
        def loop(l: Seq[Unit < S]): Unit < S =
            if l.isEmpty then ()
            else l.head.andThen(loop(l.tail))
        loop(v)
    end collectUnit

    def fill[T, S](n: Int)(v: => T < S): Seq[T] < S =
        val r = new Array[Any](n).asInstanceOf[Array[T]]
        def loop(i: Int): Seq[T] < S =
            if i == n then
                r.toIndexedSeq
            else
                v.map { e =>
                    r(i) = e
                    loop(i + 1)
                }
        loop(0)
    end fill
end Seqs
