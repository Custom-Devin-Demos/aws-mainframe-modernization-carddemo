/* LD_PRELOAD shim: freezes the wall clock at FIXED_EPOCH (default 2022-07-06T00:00:00Z,
 * the JobHarness clock) with zero sub-second part, so FUNCTION CURRENT-DATE is deterministic. */
#define _GNU_SOURCE
#include <stdlib.h>
#include <sys/time.h>
#include <time.h>

static time_t fixed_epoch(void) {
    const char *s = getenv("FIXED_EPOCH");
    return s ? (time_t) atoll(s) : (time_t) 1657065600;
}

int clock_gettime(clockid_t clk, struct timespec *ts) {
    (void) clk;
    ts->tv_sec = fixed_epoch();
    ts->tv_nsec = 0;
    return 0;
}

int gettimeofday(struct timeval *tv, void *tz) {
    (void) tz;
    if (tv) {
        tv->tv_sec = fixed_epoch();
        tv->tv_usec = 0;
    }
    return 0;
}

time_t time(time_t *t) {
    time_t v = fixed_epoch();
    if (t) {
        *t = v;
    }
    return v;
}
