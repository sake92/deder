
# remove all worktrees except main
git worktree list | tail -n +2 | awk '{print $1}' | xargs -I {} git worktree remove --force {}
rm -rf .worktrees

# remove all branches except main
git checkout main && git branch | grep -v "main" | xargs git branch -D


# tmux asciinema session record
https://gist.github.com/worldofprasanna/1861b182103cef452ec58471679a7e5b
Start a new tmux named session tmux new -s terminal-capture
Split the screen using these commands,
vertical split <C-b>"
horizontal split <C-b>%
To navigate between the panes,
To goto Left pane <C-b> left-key
To goto Right pane <C-b> right-key
To goto Top pane <C-b> up-key
To goto Down pane <C-b> down-key
Detach the session tmux <C-b>+d
Record the tmux session with asciinema asciinema rec -c "tmux attach -t terminal-capture"
Detach the tmux session, save the recording and convert it into gif
Enjoy !!!
