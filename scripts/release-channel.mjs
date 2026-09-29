import {pathToFileURL} from 'node:url';

export function releaseChannel(version, prerelease) {
  const match = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.(0|[1-9]\d*))?$/.exec(version ?? '');
  if (!match) throw new Error('Expected X.Y.Z or X.Y.Z-beta.N (alpha/rc also supported)');
  const experimental = Boolean(match[4]);
  if (prerelease !== experimental) throw new Error('Version and GitHub prerelease flag must agree');
  return experimental ? 'next' : 'latest';
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (!['true', 'false'].includes(process.argv[3])) throw new Error('Expected true/false prerelease flag');
    console.log(releaseChannel(process.argv[2], process.argv[3] === 'true'));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
