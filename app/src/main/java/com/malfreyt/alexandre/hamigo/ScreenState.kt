package com.malfreyt.alexandre.hamigo

/** Small stable UI keys, separate from progress and from transient back-gesture previews. */
internal fun AppModel.visibleScreenKey():String=when {
    session!=null->"session-${session!!.attemptId}"
    resource!=null->"reference-${resource!!.id}"
    lesson!=null->"lesson-${lesson!!.id}-${lessonOpening}"
    else->"page-$route"
}
