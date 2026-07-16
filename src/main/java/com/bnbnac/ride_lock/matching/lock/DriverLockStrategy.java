package com.bnbnac.ride_lock.matching.lock;

public interface DriverLockStrategy {

	boolean tryAssign(Long driverId);

}
