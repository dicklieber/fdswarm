/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package rigctltool

import fdswarm.model.Band
import mainargs.{ParserForMethods, arg, main}

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.{Socket, SocketTimeoutException}

/** Command-line REPL for exercising a running rigctld instance.
  *
  * Usage: rigctlTool --port <port> [--host <host>]
  *
  * Prompts for rigctld commands, sends them over TCP, and prints the response.
  * Type 'q' or press Ctrl-D to exit.
  */
object RigCtlTool:

  @main
  def run(
      @arg(short = 'p', doc = "TCP port where rigctld is listening")
      port: Int,
      @arg(short = 'h', doc = "Hostname where rigctld is listening (default: localhost)")
      host: String = "localhost"
  ): Unit =
    println(s"Connecting to rigctld at $host:$port ...")
    try
      val socket = new Socket(host, port)
      // Short read timeout so we know when a response has finished arriving.
      socket.setSoTimeout(400)
      val out   = new PrintWriter(socket.getOutputStream, true)
      val in    = new BufferedReader(new InputStreamReader(socket.getInputStream))

      println(s"Connected. Enter rigctld commands (e.g. f, m, q to quit):")
      var running = true
      while running do
        print("> ")
        System.out.flush()
        val line = scala.io.StdIn.readLine()
        if line == null || line.trim.equalsIgnoreCase("q") then
          running = false
        else if line.trim.nonEmpty then
          out.print(line.trim + "\n")
          out.flush()
          printResponse(in, line.trim) match
            case Left(msg) =>
              println(s"[connection error: $msg]")
              running = false
            case Right(()) => ()

      socket.close()
      println("\nDisconnected.")
    catch
      case e: java.net.ConnectException =>
        System.err.println(s"Cannot connect to rigctld at $host:$port: ${e.getMessage}")
        sys.exit(1)

  /** Reads response lines until an RPRT line or a read timeout signals end-of-response.
    * Annotates frequency responses with band information from [[Band.fromFrequency]].
    */
  private def printResponse(in: BufferedReader, cmd: String): Either[String, Unit] =
    val sb      = StringBuilder()
    var reading = true
    while reading do
      try
        val responseLine = in.readLine()
        if responseLine == null then
          return Left("connection closed by rigctld")
        sb.append(responseLine).append("\n")
        if responseLine.startsWith("RPRT") then reading = false
      catch
        case _: SocketTimeoutException => reading = false

    val response = sb.toString()
    print(response)

    // If this looks like a frequency query, also show the band.
    if cmd == "f" || cmd == "\\get_freq" then
      response.linesIterator.find(l => l.trim.matches("\\d+")).foreach { freqLine =>
        Band.fromFrequency(freqLine.trim.toLong).foreach { band =>
          println(s"  [band: ${band.name}]")
        }
      }

    System.out.flush()
    Right(())

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toIndexedSeq)
