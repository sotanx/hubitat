library (
  author: "David Hebert",
  category: "lib",
  description: "helpers",
  name: "utils",
  namespace: "helperLib",
  documentationLink: ""
)

def currentDateTime() {
    def currTime = new Date();
    if (debugTimeDelta != null && debugTimeDelta > 0) {
        // For testing purpose, we can travel in time!
        def val = currTime.getTime();
        val += (debugTimeDelta*1000*3600);
        currTime = new Date(val);
        // trace("now (delta): ${currTime.toString()}", "debug");
    } else {
        // trace("now: ${currTime.toString()}", "debug");
    } 
    return currTime;
}

def isExpired(Date timestamp, long delay) {
    def elapsed = currentDateTime() - timestamp;
    if ( elapsed > ( delay * 1000 ) ) {
        return true;
    }
    return false;
}

def isExpired(long timestamp, long delay) {
    def elapsed = currentDateTime().getTime() - timestamp;
    if ( elapsed > ( delay * 1000 ) ) {
        return true;
    }
    return false;
}

def getElapsedSeconds(Date timestamp) {
    def elapsed = currentDateTime() - timestamp;
    return elapsed / 1000;
}

def getElapsedSeconds(long timestamp) {
    def elapsed = currentDateTime().getTime() - timestamp;
    return elapsed / 1000;
}

def isSunset() {
    if (location.getMode() == "Night") {
        return false;
    }
    return true;
}

def getMidnight() {
    def cur = currentDateTime();
    cur.hours = 0;
    cur.minutes = 0;
    cur.seconds = 0;
    cur += 1;
    return cur;
}

def trace(message, level) {
    def output = "";
    if ( thisName != null ) {
        // Application
        output = "[${thisName}] ${message}";
    } else {
        // device
        output = "[${device.getLabel()}] ${message}";
    }
    if (level == "debug") {
        if (debugEnabled == true) { 
            log.debug output
        }        
    } else if (level == "info") {
        log.info output
    } else if (level == "error") {
        log.error output
    }
}