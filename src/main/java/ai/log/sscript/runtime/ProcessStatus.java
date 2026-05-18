package ai.log.sscript.runtime;

public enum ProcessStatus {
    IDLE, // registered but not yet running (event handler waiting)
    RUNNING, // actively executing
    WAITING, // paused by 'wait' command
    DONE, // finished successfully
    ERROR // finished with error
}
