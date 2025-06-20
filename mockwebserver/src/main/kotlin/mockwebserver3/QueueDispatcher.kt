/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mockwebserver3

import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.logging.Logger

/**
 * Default dispatcher that processes a script of responses. Populate the script by calling
 * [enqueue].
 */
public open class QueueDispatcher : Dispatcher() {
  protected val responseQueue: BlockingQueue<MockResponse> = LinkedBlockingQueue()
  private var failFastResponse: MockResponse? = null

  @Throws(InterruptedException::class)
  override fun dispatch(request: RecordedRequest): MockResponse {
    // To permit interactive/browser testing, ignore requests for favicons.
    val requestLine = request.requestLine
    if (requestLine == "GET /favicon.ico HTTP/1.1") {
      logger.info("served $requestLine")
      return MockResponse(code = HttpURLConnection.HTTP_NOT_FOUND)
    }

    if (failFastResponse != null && responseQueue.peek() == null) {
      // Fail fast if there's no response queued up.
      return failFastResponse!!
    }

    val result = responseQueue.take()

    // If take() returned because we're shutting down, then enqueue another dead letter so that any
    // other threads waiting on take() will also return.
    if (result == DEAD_LETTER) responseQueue.add(DEAD_LETTER)

    return result
  }

  public override fun peek(): MockResponse = responseQueue.peek() ?: failFastResponse ?: super.peek()

  public open fun enqueue(response: MockResponse) {
    responseQueue.add(response)
  }

  public open fun clear() {
    responseQueue.clear()
  }

  public override fun close() {
    responseQueue.add(DEAD_LETTER)
  }

  public open fun setFailFast(failFast: Boolean) {
    setFailFast(
      failFastResponse =
        when {
          failFast -> MockResponse(code = HttpURLConnection.HTTP_NOT_FOUND)
          else -> null
        },
    )
  }

  public open fun setFailFast(failFastResponse: MockResponse?) {
    this.failFastResponse = failFastResponse
  }

  private companion object {
    /**
     * Enqueued on shutdown to release threads waiting on [dispatch]. Note that this response
     * isn't transmitted because the connection is closed before this response is returned.
     */
    private val DEAD_LETTER = MockResponse(code = HTTP_UNAVAILABLE)

    private val logger = Logger.getLogger(QueueDispatcher::class.java.name)
  }
}
