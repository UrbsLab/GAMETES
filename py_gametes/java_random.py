"""A small, dependency-free implementation of ``java.util.Random``.

GAMETES promises that a supplied seed recreates a run.  Using Python's
Mersenne Twister would preserve repeatability within Python, but it would not
recreate the Java v2.2 sequence.  This class implements the Java 48-bit LCG,
bounded integers, doubles, longs, and the cached Gaussian transform used by
the original JAR.
"""

from __future__ import annotations

import math
import time
from typing import Optional


class JavaRandom:
    """Python-facing equivalent of Java's ``java.util.Random``."""

    _MULTIPLIER = 0x5DEECE66D
    _ADDEND = 0xB
    _MASK = (1 << 48) - 1

    def __init__(self, seed: Optional[int] = None) -> None:
        self._seed = 0
        self._have_next_next_gaussian = False
        self._next_next_gaussian = 0.0
        self.seed(seed)

    def seed(self, value: Optional[int] = None) -> None:
        if value is None:
            value = time.time_ns() ^ id(self)
        self._seed = (int(value) ^ self._MULTIPLIER) & self._MASK
        self._have_next_next_gaussian = False

    def _next(self, bits: int) -> int:
        self._seed = (self._seed * self._MULTIPLIER + self._ADDEND) & self._MASK
        return self._seed >> (48 - bits)

    @staticmethod
    def _signed(value: int, bits: int) -> int:
        sign = 1 << (bits - 1)
        return value - (1 << bits) if value & sign else value

    def next_int(self, bound: Optional[int] = None) -> int:
        if bound is None:
            return self._signed(self._next(32), 32)
        if bound <= 0 or bound > 0x7FFFFFFF:
            raise ValueError("bound must be between 1 and 2**31 - 1")

        if (bound & (bound - 1)) == 0:
            return (bound * self._next(31)) >> 31

        while True:
            bits = self._next(31)
            value = bits % bound
            # Java performs this expression as a signed 32-bit int.
            if bits - value + (bound - 1) <= 0x7FFFFFFF:
                return value

    def randrange(self, stop: int) -> int:
        return self.next_int(stop)

    def next_long(self) -> int:
        high = self._signed(self._next(32), 32)
        low = self._signed(self._next(32), 32)
        value = (high << 32) + low
        return self._signed(value & 0xFFFFFFFFFFFFFFFF, 64)

    def getrandbits(self, bits: int) -> int:
        if bits <= 0:
            raise ValueError("number of bits must be greater than zero")
        value = 0
        remaining = bits
        while remaining:
            take = min(remaining, 32)
            value = (value << take) | (self._next(take) & ((1 << take) - 1))
            remaining -= take
        return value

    def next_double(self) -> float:
        return ((self._next(26) << 27) + self._next(27)) / float(1 << 53)

    def random(self) -> float:
        return self.next_double()

    def next_gaussian(self) -> float:
        if self._have_next_next_gaussian:
            self._have_next_next_gaussian = False
            return self._next_next_gaussian

        while True:
            v1 = (2.0 * self.next_double()) - 1.0
            v2 = (2.0 * self.next_double()) - 1.0
            s = (v1 * v1) + (v2 * v2)
            if s < 1.0 and s != 0.0:
                multiplier = math.sqrt((-2.0 * math.log(s)) / s)
                self._next_next_gaussian = v2 * multiplier
                self._have_next_next_gaussian = True
                return v1 * multiplier

    def gauss(self, mean: float, standard_deviation: float) -> float:
        return mean + (standard_deviation * self.next_gaussian())
