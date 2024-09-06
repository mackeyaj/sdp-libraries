package libraries.git.steps

def source_branch = git_distributions.fetch().get_source_branch()
void call() {
    node() {
    sh 'printenv'
    println source_branch
    }
}
