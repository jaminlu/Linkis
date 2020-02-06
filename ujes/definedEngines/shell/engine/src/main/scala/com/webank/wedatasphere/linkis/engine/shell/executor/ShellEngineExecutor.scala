package com.webank.wedatasphere.linkis.engine.shell.executor

import java.io.{BufferedReader, InputStreamReader}

import com.webank.wedatasphere.linkis.engine.execute.{EngineExecutor, EngineExecutorContext}
import com.webank.wedatasphere.linkis.protocol.engine.JobProgressInfo
import com.webank.wedatasphere.linkis.resourcemanager.{LoadInstanceResource, Resource}
import com.webank.wedatasphere.linkis.rpc.Sender
import com.webank.wedatasphere.linkis.scheduler.executer._
import com.webank.wedatasphere.linkis.engine.shell.conf.ShellEngineConfiguration
import com.webank.wedatasphere.linkis.engine.shell.exception.ShellCodeErrorException
import org.apache.commons.lang.StringUtils
import com.webank.wedatasphere.linkis.engine.execute.EngineExecutor

/**
  * created by cooperyang on 2019/5/14
  * Description:
  */
class ShellEngineExecutor(user:String)
  extends EngineExecutor(ShellEngineConfiguration.OUTPUT_LIMIT.getValue, isSupportParallelism = false) with SingleTaskOperateSupport with SingleTaskInfoSupport{

  private val name:String = Sender.getThisServiceInstance.getInstance

  override def getName: String = name

  override def getActualUsedResources: Resource =
    new LoadInstanceResource(Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory(), 2, 1)

  private var process:Process = _

  override protected def executeLine(engineExecutorContext: EngineExecutorContext, code: String): ExecuteResponse = {
    val trimCode = code.trim
    logger.info(s"user $user begin to run code $trimCode")
    try{
      val processBuilder:ProcessBuilder = new ProcessBuilder(generateRunCode(code):_*)
      processBuilder.redirectErrorStream(true)
      process = processBuilder.start()
      val bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream))
      val errorsReader = new BufferedReader(new InputStreamReader(process.getErrorStream))
      var line:String = null
      while({line = bufferedReader.readLine(); line != null}){
        logger.info(line)
        engineExecutorContext.appendStdout(line)
      }
      val errorLog = Stream.continually(errorsReader.readLine).takeWhile(_ != null).mkString("\n")
      val exitCode = process.waitFor()
      if (StringUtils.isNotEmpty(errorLog) || exitCode != 0){
        logger.error(s"exitCode is $exitCode")
        logger.error(errorLog)
        engineExecutorContext.appendStdout("shell执行失败")
        engineExecutorContext.appendStdout(errorLog)
        ErrorExecuteResponse("run shell failed", ShellCodeErrorException())
      }else SuccessExecuteResponse()
    }catch{
      case e:Exception => {
        logger.error("Execute shell code failed, reason:", e)
        ErrorExecuteResponse("run shell failed" ,e)
      }
      case t:Throwable => ErrorExecuteResponse("执行shell进程内部错误", t)
    }
  }


  private def generateRunCode(code: String):Array[String] = {
    Array("sh", "-c", code)
  }




  override protected def executeCompletely(engineExecutorContext: EngineExecutorContext, code: String, completedLine: String): ExecuteResponse = {
    val completeCode = code + completedLine
    executeLine(engineExecutorContext, completeCode)
  }

  override def kill(): Boolean = {
    try{
      process.destroy()
      true
    }catch{
      case e:Exception => logger.error(s"kill process ${process.toString} failed ", e)
        false
      case t:Throwable => logger.error(s"kill process ${process.toString} failed " ,t)
        false
    }
  }

  override def pause(): Boolean = true

  override def resume(): Boolean = true

  override def progress(): Float = 0.5f

  override def getProgressInfo: Array[JobProgressInfo] = Array()

  override def log(): String = ""

  override def close(): Unit = {

  }
}
