package com.limelight.ligase.library

object LibraryOperationGate {
    fun canOperate(
        connectivity: LibraryConnectivity,
        accessMode: String?,
    ): Boolean = connectivity == LibraryConnectivity.ONLINE && accessMode == "operate"
}
