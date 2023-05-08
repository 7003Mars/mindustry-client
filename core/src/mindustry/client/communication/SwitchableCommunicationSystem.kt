package mindustry.client.communication

class SwitchableCommunicationSystem(val systems: List<CommunicationSystem>) : CommunicationSystem() {

    constructor(vararg communicationSystems: CommunicationSystem) : this(communicationSystems.toMutableList())

    var activeCommunicationSystem: CommunicationSystem = systems[0]

    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()

    override val id by activeCommunicationSystem::id
    override val MAX_LENGTH by activeCommunicationSystem::MAX_LENGTH
    override val RATE by activeCommunicationSystem::RATE

    override val secure by activeCommunicationSystem::secure

    override fun send(bytes: ByteArray) {
        activeCommunicationSystem.send(bytes)
    }

    override fun init() {
        for (system in systems) {
            system.init()
        }
    }

    override fun clearListeners() {
        listeners.clear()
        syncListeners()
    }

    override fun addListener(listener: (input: ByteArray, sender: Int) -> Unit) {
        listeners.add(listener)
        syncListeners()
    }

    override fun addAllListeners(items: Collection<(input: ByteArray, sender: Int) -> Unit>) {
        listeners.addAll(items)
        syncListeners()
    }

    private fun syncListeners() {
        for (system in systems) {
            system.clearListeners()
            system.addAllListeners(listeners)
        }
    }
}
