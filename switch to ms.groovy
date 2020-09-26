definition(
    name: "Switch-Motion",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Turn a virtual switch into Motion Sensor",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this Virtual Motion device", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
			input "theSwitch", "capability.switch", title: "Select switch", submitOnChange: true, required: true, multiple: true
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	log.info "updated"
    unsubscribe()
	initialize()
}

def initialize() {
	log.info "init"
    def motionDev = getChildDevice("SwitchMotion_${app.id}")
	if(!motionDev) motionDev = addChildDevice("hubitat", "Virtual Motion Sensor", "SwitchMotion_${app.id}", null, [label: thisName, name: thisName])
	subscribe(theSwitch, "switch", switchHandler)
}

def switchHandler(evt) {
	def motionDev = getChildDevice("SwitchMotion_${app.id}")
	log.info evt.value
    if(evt.value == "on") motionDev.active() else motionDev.inactive()
	log.info "Switch $evt.device $evt.value"
}