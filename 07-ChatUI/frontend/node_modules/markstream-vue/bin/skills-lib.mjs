import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
export const packageRoot = path.resolve(__dirname, '..')
export const defaultSourceRoot = path.join(packageRoot, '.agents', 'skills')

export function printInstallHelp(command = 'markstream-vue skills install', defaultMode = 'copy') {
  console.log(`Usage: ${command} [options]

Install bundled Markstream skills from .agents/skills into a local agent skill directory.

Options:
  --target <agents|codex|path>  Target directory preset or custom path.
                                Default: agents
  --mode <symlink|copy>         Install mode. Default: ${defaultMode}
  --force                       Replace existing non-symlink targets
  --dry-run                     Print actions without writing anything
  --help                        Show this help

Examples:
  ${command}
  ${command} --mode copy
  ${command} --target codex
  ${command} --target ./tmp/skills-test --mode copy --force
`)
}

export function parseInstallArgs(argv, defaults = {}) {
  const args = {
    target: defaults.target ?? 'agents',
    mode: defaults.mode ?? 'copy',
    force: false,
    dryRun: false,
    help: false,
  }

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    if (arg === '--')
      continue
    if (arg === '--target') {
      args.target = argv[++i] ?? ''
    }
    else if (arg === '--mode') {
      args.mode = argv[++i] ?? ''
    }
    else if (arg === '--force') {
      args.force = true
    }
    else if (arg === '--dry-run') {
      args.dryRun = true
    }
    else if (arg === '--help' || arg === '-h') {
      args.help = true
    }
    else {
      throw new Error(`Unknown argument: ${arg}`)
    }
  }

  if (!['symlink', 'copy'].includes(args.mode))
    throw new Error(`Invalid --mode value: ${args.mode}`)

  if (!args.target)
    throw new Error('Missing --target value')

  return args
}

export function resolveTargetRoot(target) {
  const home = os.homedir()
  if (target === 'agents')
    return path.join(home, '.agents', 'skills')
  if (target === 'codex') {
    const codexHome = process.env.CODEX_HOME || path.join(home, '.codex')
    return path.join(codexHome, 'skills')
  }
  return path.resolve(process.cwd(), target)
}

async function exists(filePath) {
  try {
    await fs.access(filePath)
    return true
  }
  catch {
    return false
  }
}

export async function listSkillDirs(sourceRoot = defaultSourceRoot) {
  const dirents = await fs.readdir(sourceRoot, { withFileTypes: true })
  const skillDirs = []
  for (const dirent of dirents) {
    if (!dirent.isDirectory())
      continue
    const fullPath = path.join(sourceRoot, dirent.name)
    if (await exists(path.join(fullPath, 'SKILL.md')))
      skillDirs.push(fullPath)
  }
  return skillDirs.sort()
}

export async function listSkillNames(sourceRoot = defaultSourceRoot) {
  return (await listSkillDirs(sourceRoot)).map(dir => path.basename(dir))
}

async function ensureDir(dirPath, dryRun) {
  if (dryRun)
    return
  await fs.mkdir(dirPath, { recursive: true })
}

async function readLinkedTarget(destPath) {
  try {
    const rawTarget = await fs.readlink(destPath)
    return path.resolve(path.dirname(destPath), rawTarget)
  }
  catch {
    return null
  }
}

async function removeExisting(destPath, force, dryRun) {
  const stat = await fs.lstat(destPath)
  if (stat.isSymbolicLink()) {
    if (!dryRun)
      await fs.rm(destPath, { recursive: true, force: true })
    return 'replaced-symlink'
  }
  if (!force)
    throw new Error(`Target already exists and is not a symlink: ${destPath}. Re-run with --force to replace it.`)
  if (!dryRun)
    await fs.rm(destPath, { recursive: true, force: true })
  return 'replaced-existing'
}

async function installSkill({ srcPath, destPath, mode, force, dryRun }) {
  if (await exists(destPath)) {
    if (mode === 'symlink') {
      const linkedTarget = await readLinkedTarget(destPath)
      if (linkedTarget === srcPath)
        return { status: 'skipped', detail: 'already-linked' }
    }
    const detail = await removeExisting(destPath, force, dryRun)
    if (mode === 'copy') {
      if (!dryRun)
        await fs.cp(srcPath, destPath, { recursive: true })
      return { status: 'installed', detail }
    }
    if (!dryRun)
      await fs.symlink(srcPath, destPath, process.platform === 'win32' ? 'junction' : 'dir')
    return { status: 'installed', detail }
  }

  if (!dryRun) {
    if (mode === 'copy')
      await fs.cp(srcPath, destPath, { recursive: true })
    else
      await fs.symlink(srcPath, destPath, process.platform === 'win32' ? 'junction' : 'dir')
  }

  return { status: 'installed', detail: 'new' }
}

export async function runSkillsInstall({ sourceRoot = defaultSourceRoot, target, mode, force = false, dryRun = false }) {
  const targetRoot = resolveTargetRoot(target)
  const skillDirs = await listSkillDirs(sourceRoot)

  if (skillDirs.length === 0)
    throw new Error(`No skill directories with SKILL.md found under ${sourceRoot}`)

  await ensureDir(targetRoot, dryRun)

  const summary = []
  for (const srcPath of skillDirs) {
    const skillName = path.basename(srcPath)
    const destPath = path.join(targetRoot, skillName)
    const result = await installSkill({
      srcPath,
      destPath,
      mode,
      force,
      dryRun,
    })
    summary.push({ skillName, destPath, ...result })
  }

  return {
    sourceRoot,
    targetRoot,
    mode,
    dryRun,
    summary,
  }
}

export function printInstallSummary(result) {
  const label = result.dryRun ? 'Dry run complete' : 'Skill install complete'
  console.log(`${label}: ${result.summary.length} skill(s) processed.`)
  console.log(`Source: ${result.sourceRoot}`)
  console.log(`Target: ${result.targetRoot}`)
  console.log(`Mode: ${result.mode}`)

  for (const item of result.summary)
    console.log(`- ${item.skillName}: ${item.status} (${item.detail}) -> ${item.destPath}`)

  if (!result.dryRun && result.mode === 'symlink')
    console.log('These installs are symlinks, so editing the source skills updates the installed versions too.')
}
