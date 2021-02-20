package com.luke.blurlayout.bitmappool

open class LruPoolStrategyAdapter(val delegate: LruPoolStrategy) : LruPoolStrategy by delegate