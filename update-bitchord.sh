#!/usr/bin/env bash
# Follow upstream BitChord's desktop code while keeping the local changes (Selaras menu, Linux
# window fix, build script) committed on the "selaras" branch, then build and install.
#
#   ./update-bitchord.sh              fetch, merge, build, install
#   ./update-bitchord.sh --no-build   fetch and merge only
#   ./update-bitchord.sh --check      only report whether there is something new
#
# Upstream does not keep the desktop app on main yet; it lives on whichever branch carries the
# latest desktop work. The script follows that line: it remembers the upstream commit it merged
# last, and picks the newest upstream branch that still contains the desktop app and builds on
# that commit. If the desktop app reaches main one day, main is picked up the same way.
set -euo pipefail
cd "$(dirname "$0")"

build=1
check=0
for arg in "$@"; do
    case "$arg" in
        --no-build) build=0 ;;
        --check) check=1 ;;
        *) echo "unknown option: $arg" >&2; exit 64 ;;
    esac
done

[[ "$(git branch --show-current)" == selaras ]] || { echo "Switch to the 'selaras' branch first (git switch selaras)." >&2; exit 1; }
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "There are uncommitted changes. Commit them first so they are kept:" >&2
    git status --short >&2
    exit 1
fi

echo "Fetching github.com/kushagrasinghx/BitChord…"
git fetch -q --prune --tags origin '+refs/heads/*:refs/remotes/origin/*'

base=$(git config --get bitchord.upstream-commit || true)
if [[ -z "$base" ]]; then
    echo "No upstream recorded yet: git config bitchord.upstream-commit <commit>" >&2
    exit 1
fi

best=""
while read -r ref; do
    [[ "$ref" == origin/HEAD || "$ref" == origin ]] && continue
    git cat-file -e "$ref:desktopApp" 2>/dev/null || continue
    git merge-base --is-ancestor "$base" "$ref" || continue
    best=$ref
    break
done < <(git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/remotes/origin)

if [[ -z "$best" ]]; then
    echo "No upstream branch continues the desktop code from ${base:0:8} any more." >&2
    echo "Pick one by hand: git config bitchord.upstream-commit <commit>" >&2
    exit 1
fi

target=$(git rev-parse "$best")
if git merge-base --is-ancestor "$target" HEAD; then
    echo "Up to date: $best ($(git log -1 --format='%h %ad' --date=short "$target")) is already merged."
    exit 0
fi

echo "New upstream desktop code on $best:"
git log --format='  %ad %h %s' --date=short "HEAD..$target" -- desktopApp shared sharedUi | head -25
echo "  ($(git rev-list --count "HEAD..$target") commits in total)"
[[ $check == 1 ]] && exit 0

if ! git merge --no-edit -m "Merge upstream $best into selaras" "$target"; then
    git merge --abort || true
    echo >&2
    echo "The upstream changes conflict with the local ones; nothing was changed." >&2
    echo "Merge by hand: git merge $best, fix the files, git commit." >&2
    exit 2
fi
git config bitchord.upstream-commit "$target"
git config bitchord.upstream-branch "$best"
echo "Merged $best."

if [[ $build == 1 ]]; then
    JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}" ./build-install-fedora.sh
fi
