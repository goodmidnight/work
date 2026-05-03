package io.goodmidnight.transfer.ui.core.exception

import android.util.Log
import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.CommonErrorCode
import io.goodmidnight.transfer.core.exception.CommonException
import io.goodmidnight.transfer.core.exception.ErrorCode
import io.goodmidnight.transfer.ui.core.viewmodel.BaseError

data class AppError(
    override val cause: ApplicationException,
    val uiMessage: String? = null,
) : BaseError {

    fun handleError() {
        Log.e(TAG, this.toString())
        cause.printStackTrace()
    }

    override fun toString(): String {
        return "$TAG(cause=$cause, uiMessage=$uiMessage)"
    }

    companion object {
        private val TAG: String = AppError::class.java.simpleName


        fun of(throwable: Throwable, uiMessage: String? = null): AppError =
            when (throwable) {
                is Error -> throw throwable
                is ApplicationException -> AppError(
                    cause = throwable,
                    uiMessage = throwable.errorCode.toUiMessage()
                )

                else -> of(
                    throwable = CommonException.UnknownException(
                        message = throwable.message ?: "An unknown error occurred.",
                        cause = throwable.cause,
                        errorCode = CommonErrorCode.UNKNOWN_ERROR,
                    ),
                    uiMessage = uiMessage
                )
            }

        fun ErrorCode.toUiMessage(): String? {
            return if (this is CommonErrorCode) when (this) {
                CommonErrorCode.UNKNOWN_ERROR -> "Unknown Error"
            }
            else null
        }
    }
}

