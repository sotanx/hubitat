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
        section {
            input "minutes", "number", required: true, title: "Away after X minutes of inactivity"
        }
        section {        
			input "presenceSensors", "capability.presenceSensor", title: "Select Presence Sensors", submitOnChange: true, required: true, multiple: true
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

    state.clear()    
    
    // Create master presence device
    masterPresence = getChildDevice("Presence_${app.id}")
	if(!masterPresence)
    {
        masterPresence = addChildDevice("hubitat", "Virtual Presence", "Presence_${app.id}", null, [label: thisName, name: thisName])
    }
    
    // Subscribe to all devices
    subscribe(presenceSensors, "presence", presenceHandler)
    
    // Reset states
    initializeDefaultState()
}

// Private methods

def checkStatus() {
    // log.info "checkStatus"
   
    masterPresence = getChildDevice("Presence_${app.id}")
    
    def presenceDetected = false
    state.deviceStatus.each { key, val ->
        if ( val == true ) {
            presenceDetected = true
        }
    }

    if (presenceDetected == true) {
        setDeviceState(masterPresence, true)
    } else {
        // how long since last motion
        def elapsed = now() - state.lastMotion
        //log.info "elapsed ${elapsed}"
        if ( elapsed > ( minutes * 60 * 1000 ) ) {
            setDeviceState(masterPresence, false)
        }
    }
    runIn(30, checkStatus)
}

def initializeDefaultState() {
    log.info "initializeDefaultState"
    setDeviceState(masterPresence, false)
    state.lastMotion = 0
    state.deviceStatus = [:]
    presenceSensors.each { device ->
        // log.info "initializeDefaultState ${device.displayName} ${device.currentValue("presence")}"
        if ( device.currentValue("presence") == "present" ) {
            state.deviceStatus["${device.displayName}"] = true
        } else {
            state.deviceStatus["${device.displayName}"] = false
        }
    }
    checkStatus()
}

def setDeviceState(device, present) {
    if ( present == true ) {
        if ( device.currentValue("presence") != "present" ) {
            device.arrived()
        }
    } else {
        if ( device.currentValue("presence") != "not present" ) {
            device.departed()
        }
    }
    state.masterState = present
}

// Event handlers

def presenceHandler(evt) {
    log.info "presenceHandler ${evt.value} on ${evt.displayName} (${evt.deviceId})"
    if ( evt.value == "present" ) {
        state.deviceStatus["${evt.displayName}"] = true
        state.lastMotion = now()
        masterPresence = getChildDevice("Presence_${app.id}")
        setDeviceState(masterPresence, true)
    } else {
        state.deviceStatus["${evt.displayName}"] = false
    }
}
