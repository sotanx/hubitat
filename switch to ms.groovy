definition(
    name: "Away detector",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Anybody home?",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Application name", submitOnChange: true
            if(thisName) app.updateLabel("$thisName")
        }
        section("Setup") {
            input "pollRate", "number", required: true, title: "Poll rate (seconds)"
            input "timeout", "number", required: true, title: "Away after X seconds of inactivity"
            input "powerThreshold", "number", required: true, title: "min power to declare active"
        }
        section {        
			input "presenceSensors", "capability.presenceSensor", title: "Select Presence Sensors", submitOnChange: true, required: false, multiple: true
		}
        section {        
			input "motionSensors", "capability.motionSensor", title: "Select motion Sensors", submitOnChange: true, required: false, multiple: true
		}
        section {        
			input "contactSensors", "capability.contactSensor", title: "Select contact Sensors", submitOnChange: true, required: false, multiple: true
		}                
        section {        
			input "powerMeters", "capability.powerMeter", title: "Select power meters", submitOnChange: true, required: false, multiple: true
		}                
	}
}

def installed() {
	initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
	// log.info "initialize"
    
    // Create master presence device
    masterPresence = getChildDevice("Presence_${app.id}")
	if(!masterPresence)
    {
        addChildDevice("hubitat", "Virtual Presence", "Presence_${app.id}", null, [label: thisName, name: thisName])
    }
    
    // Subscribe to all devices
    subscribe(presenceSensors, "presence", presenceHandler)
    subscribe(motionSensors, "motion", motionHandler)
    subscribe(contactSensors, "contact", contactHandler)
    subscribe(powerMeters, "power", powerHandler)
    
    // Start!
    state.lastActivity = 0
    checkStatus()
}

// Private methods

def checkStatus() {
    // log.info "checkStatus"
    
    def presenceDetected = false

    presenceSensors.each { device ->
        if ( device.currentValue("presence") == "present" ) {
            presenceDetected = true
        }
    }
    
    if ( presenceDetected == false ) {
        motionSensors.each { device ->
            if ( device.currentValue("motion") == "active" ) {
                presenceDetected = true
            }
        }    
    }
    
    if ( presenceDetected == false ) {
        powerMeters.each { device ->
            if ( device.currentValue("power") > powerThreshold ) {
                presenceDetected = true
            }
        }    
    }    
    
    if (presenceDetected == true) {
        setMasterState(true)
    } else {
        // how long since last activity
        def elapsed = now() - state.lastActivity
        if ( elapsed > ( timeout * 1000 ) ) {
            setMasterState(false)
        }
    }
    runIn(pollRate, checkStatus)
}

def setMasterState(present) {
    device = getChildDevice("Presence_${app.id}")
    state.masterState = present
    if ( present == true ) {
        state.lastActivity = now()
        if ( device.currentValue("presence") != "present" ) {
            device.arrived()
        }
    } else {
        if ( device.currentValue("presence") != "not present" ) {
            device.departed()
        }
    }
}

// Event handlers

def presenceHandler(evt) {
    // log.info "presenceHandler ${evt.value} on ${evt.displayName} (${evt.deviceId})"
    if ( evt.value == "present" ) {
        setMasterState(true)
    }
}

def motionHandler(evt) {
    // log.info "motionHandler ${evt.value} on ${evt.displayName} (${evt.deviceId})"
    if ( evt.value == "active" ) {
        setMasterState(true)
    }
}

def contactHandler(evt) {
    // log.info "contactHandler ${evt.value} on ${evt.displayName} (${evt.deviceId})"
    // For a contact, anything == presence
    setMasterState(true)
}

def powerHandler(evt) {
    // log.info "powerHandler ${evt.value} on ${evt.displayName} (${evt.deviceId})"
    if ( evt.floatValue > powerThreshold ) {
        setMasterState(true)
    }
}

