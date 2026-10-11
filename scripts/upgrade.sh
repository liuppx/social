#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 3 ]]; then
    printf 'Usage: %s <prepare|post-copy|finalize> <current-dir> <target-dir>\n' "$0" >&2
    exit 1
fi

phase=$1
target_dir=$3

case "$phase" in
    prepare|finalize)
        exit 0
        ;;
    post-copy)
        [[ -d "${target_dir}/web/dist" ]] || {
            printf 'Missing web dist: %s\n' "${target_dir}/web/dist" >&2
            exit 1
        }
        rm -rf /usr/share/nginx/html/social/dist
        mkdir -p /usr/share/nginx/html/social
        cp -r "${target_dir}/web/dist" /usr/share/nginx/html/social/dist
        systemctl restart nginx
        ;;
    *)
        printf 'Unsupported upgrade phase: %s\n' "$phase" >&2
        exit 1
        ;;
esac
