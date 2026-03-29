#!/usr/bin/env node

import process from 'node:process'
import { defaultPromptsRoot, listPromptNames, readPrompt } from './prompts-lib.mjs'
import {
  listSkillNames,
  parseInstallArgs,
  printInstallHelp,
  printInstallSummary,
  runSkillsInstall,
} from './skills-lib.mjs'

function printCliHelp() {
  console.log(`Usage: markstream-vue <command>

Commands:
  skills list                 List bundled skills
  skills install [options]    Install bundled skills
  prompts list                List bundled prompt templates
  prompts dir                 Print bundled prompt directory
  prompts show <name>         Print a bundled prompt template

Examples:
  markstream-vue skills list
  markstream-vue skills install
  markstream-vue skills install --target codex
  markstream-vue skills install --mode copy --force
  markstream-vue prompts list
  markstream-vue prompts show install-markstream
`)
}

async function main() {
  const argv = process.argv.slice(2)
  const [namespace, action, ...rest] = argv

  if (!namespace || namespace === '--help' || namespace === '-h' || namespace === 'help') {
    printCliHelp()
    return
  }

  if (namespace === 'prompts') {
    if (!action || action === 'help') {
      console.log(`Usage: markstream-vue prompts <list|dir|show> [name]

Examples:
  markstream-vue prompts list
  markstream-vue prompts dir
  markstream-vue prompts show install-markstream
`)
      return
    }

    if (action === 'list') {
      const promptNames = await listPromptNames()
      console.log(`Bundled prompts (${promptNames.length}):`)
      for (const promptName of promptNames)
        console.log(`- ${promptName}`)
      return
    }

    if (action === 'dir') {
      console.log(defaultPromptsRoot)
      return
    }

    if (action === 'show') {
      const [promptName] = rest
      const { content } = await readPrompt(promptName)
      process.stdout.write(content)
      if (!content.endsWith('\n'))
        process.stdout.write('\n')
      return
    }

    throw new Error(`Unknown prompts action: ${action}`)
  }

  if (namespace !== 'skills') {
    throw new Error(`Unknown command namespace: ${namespace}`)
  }

  if (!action || action === 'help') {
    printInstallHelp('markstream-vue skills install', 'copy')
    return
  }

  if (action === 'list') {
    const skillNames = await listSkillNames()
    console.log(`Bundled skills (${skillNames.length}):`)
    for (const skillName of skillNames)
      console.log(`- ${skillName}`)
    return
  }

  if (action === 'install') {
    const args = parseInstallArgs(rest, {
      target: 'agents',
      mode: 'copy',
    })
    if (args.help) {
      printInstallHelp('markstream-vue skills install', 'copy')
      return
    }
    const result = await runSkillsInstall(args)
    printInstallSummary(result)
    return
  }

  throw new Error(`Unknown skills action: ${action}`)
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error)
  process.exit(1)
})
